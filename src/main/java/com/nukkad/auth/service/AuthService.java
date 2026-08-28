package com.nukkad.auth.service;

import com.nukkad.auth.dto.AuthResponse;
import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.RegisterRequest;
import com.nukkad.auth.entity.PasswordResetToken;
import com.nukkad.auth.entity.RefreshToken;
import com.nukkad.auth.repository.PasswordResetTokenRepository;
import com.nukkad.auth.repository.RefreshTokenRepository;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.UnauthorizedException;
import com.nukkad.security.JwtService;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        UserMapper userMapper,
                        AuditService auditService,
                        GoogleTokenVerifier googleTokenVerifier,
                        PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String ip, String userAgent) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = User.builder()
                .name(request.name().trim())
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .securityRoles(new HashSet<>(Set.of(SecurityRole.USER)))
                .build();
        // saveAndFlush (not save): @CreationTimestamp is only assigned once the INSERT actually
        // runs, which Hibernate would otherwise defer to end-of-transaction — after we've already
        // read it below to build the response.
        user = userRepository.saveAndFlush(user);
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        return issueAuthResponse(user, ip, userAgent, true);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        userRepository.touchLastActiveAt(user.getId(), Instant.now());
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        return issueAuthResponse(user, ip, userAgent, false);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String rawIdToken, String ip, String userAgent) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(rawIdToken);
        String email = identity.email().toLowerCase().trim();

        boolean[] isNewUser = { false };
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            isNewUser[0] = true;
            User created = User.builder()
                    .name(identity.name() != null && !identity.name().isBlank() ? identity.name() : email)
                    .email(email)
                    // Google-authenticated accounts have no password of their own; this hash is
                    // unusable for password login and only satisfies the NOT NULL column.
                    .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .avatarUrl(identity.pictureUrl())
                    .securityRoles(new HashSet<>(Set.of(SecurityRole.USER)))
                    .build();
            return userRepository.saveAndFlush(created);
        });

        userRepository.touchLastActiveAt(user.getId(), Instant.now());
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        return issueAuthResponse(user, ip, userAgent, isNewUser[0]);
    }

    @Transactional
    public com.nukkad.auth.dto.RefreshTokenResponse refresh(String presentedRawToken, String ip, String userAgent) {
        String hash = jwtService.hashOpaqueToken(presentedRawToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (existing.getRevokedAt() != null) {
            // Reuse of an already-rotated-away token: treat as a compromise signal.
            log.warn("Refresh token reuse detected for user {}", existing.getUserId());
            revokeAllForUser(existing.getUserId());
            throw new UnauthorizedException("Refresh token has already been used; all sessions revoked");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        RefreshToken rotated = issueRefreshToken(user.getId(), ip, userAgent);
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByTokenId(rotated.getId());
        refreshTokenRepository.save(existing);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user));
        return new com.nukkad.auth.dto.RefreshTokenResponse(accessToken, rotated.rawTokenTransient, jwtService.getAccessExpirationSeconds());
    }

    @Transactional
    public void logout(String presentedRawToken) {
        String hash = jwtService.hashOpaqueToken(presentedRawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            String rawToken = jwtService.generateOpaqueToken();
            String hash = jwtService.hashOpaqueToken(rawToken);
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(hash)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            passwordResetTokenRepository.save(resetToken);
            // No SMTP integration in V1 — log the link so it can be manually delivered in dev/testing.
            log.info("Password reset requested for {}. Reset token (dev-only log): {}", user.getEmail(), rawToken);
        });
        // Always behave the same regardless of whether the email exists, to avoid user enumeration.
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        revokeAllForUser(userId);
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        String hash = jwtService.hashOpaqueToken(rawToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        revokeAllForUser(user.getId());
    }

    /**
     * Runs in its own committed transaction (REQUIRES_NEW) so the revocation survives even when
     * the caller immediately throws afterward (e.g. refresh-token-reuse detection) — otherwise
     * Spring's default rollback-on-RuntimeException would undo this side effect along with it.
     */
    private void revokeAllForUser(String userId) {
        requiresNewTransactionTemplate.executeWithoutResult(status ->
                refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                        .forEach(t -> t.setRevokedAt(Instant.now())));
    }

    @Transactional
    public java.util.List<com.nukkad.auth.dto.SessionDto> listSessions(String userId, String currentRawToken) {
        String currentHash = currentRawToken != null ? jwtService.hashOpaqueToken(currentRawToken) : null;
        return refreshTokenRepository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(userId).stream()
                .map(t -> new com.nukkad.auth.dto.SessionDto(
                        t.getId(),
                        t.getDeviceLabel() != null ? t.getDeviceLabel() : "Unknown device",
                        t.getCreatedByIp(),
                        t.getLastUsedAt(),
                        t.getCreatedAt(),
                        t.getTokenHash().equals(currentHash)))
                .toList();
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new com.nukkad.common.exception.ResourceNotFoundException("Session not found"));
        if (!token.getUserId().equals(userId)) {
            throw new com.nukkad.common.exception.ForbiddenException("Not your session");
        }
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revokeAllExcept(String userId, String currentRawToken) {
        String currentHash = currentRawToken != null ? jwtService.hashOpaqueToken(currentRawToken) : null;
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(t -> !t.getTokenHash().equals(currentHash))
                .forEach(t -> t.setRevokedAt(Instant.now()));
    }

    private AuthResponse issueAuthResponse(User user, String ip, String userAgent, boolean isNewUser) {
        RefreshToken refreshToken = issueRefreshToken(user.getId(), ip, userAgent);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user));
        return new AuthResponse(userMapper.toDto(user), accessToken, refreshToken.rawTokenTransient, jwtService.getAccessExpirationSeconds(), isNewUser);
    }

    private RefreshToken issueRefreshToken(String userId, String ip, String userAgent) {
        String raw = jwtService.generateOpaqueToken();
        Instant now = Instant.now();
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(jwtService.hashOpaqueToken(raw))
                .expiresAt(now.plusSeconds(jwtService.getRefreshExpirationSeconds()))
                .createdByIp(ip)
                .userAgent(userAgent)
                .deviceLabel(UserAgentParser.label(userAgent))
                .lastUsedAt(now)
                .build();
        token = refreshTokenRepository.save(token);
        token.rawTokenTransient = raw;
        return token;
    }

    private Set<String> roleNames(User user) {
        return user.getSecurityRoles().stream().map(Enum::name).collect(Collectors.toSet());
    }
}
