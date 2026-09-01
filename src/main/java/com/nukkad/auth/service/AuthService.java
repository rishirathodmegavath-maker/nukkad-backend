package com.nukkad.auth.service;

import com.nukkad.auth.dto.AuthResponse;
import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.RegisterRequest;
import com.nukkad.auth.dto.RegisterResponse;
import com.nukkad.auth.entity.EmailVerificationToken;
import com.nukkad.auth.entity.PasswordResetToken;
import com.nukkad.auth.entity.RefreshToken;
import com.nukkad.auth.repository.EmailVerificationTokenRepository;
import com.nukkad.auth.repository.PasswordResetTokenRepository;
import com.nukkad.auth.repository.RefreshTokenRepository;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.email.EmailService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.EmailNotVerifiedException;
import com.nukkad.common.exception.GoogleAccountNotFoundException;
import com.nukkad.common.exception.GoogleAccountNotLinkedException;
import com.nukkad.common.exception.GoogleEmailMismatchException;
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
    private static final long EMAIL_VERIFICATION_EXPIRY_SECONDS = 24L * 3600;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final EmailService emailService;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        EmailVerificationTokenRepository emailVerificationTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        UserMapper userMapper,
                        AuditService auditService,
                        GoogleTokenVerifier googleTokenVerifier,
                        EmailService emailService,
                        PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.emailService = emailService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String ip, String userAgent) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .emailVerified(false)
                .securityRoles(new HashSet<>(Set.of(SecurityRole.USER)))
                .build();
        user = userRepository.saveAndFlush(user);
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        issueVerificationEmail(user);
        return new RegisterResponse(user.getEmail(), "Account created. Check your email to verify your address before signing in.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before signing in.");
        }
        userRepository.touchLastActiveAt(user.getId(), Instant.now());
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        return issueAuthResponse(user, ip, userAgent);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = jwtService.hashOpaqueToken(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .filter(EmailVerificationToken::isUsable)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification link"));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification link"));

        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        userRepository.findByEmail(email.toLowerCase().trim())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueVerificationEmail);
        // Always behave the same regardless of whether the email exists or is already verified,
        // to avoid user enumeration.
    }

    private void issueVerificationEmail(User user) {
        String rawToken = jwtService.generateOpaqueToken();
        String hash = jwtService.hashOpaqueToken(rawToken);
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(hash)
                .expiresAt(Instant.now().plusSeconds(EMAIL_VERIFICATION_EXPIRY_SECONDS))
                .build();
        emailVerificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String rawIdToken, String ip, String userAgent) {
        return authenticateGoogleIdentity(googleTokenVerifier.verify(rawIdToken), ip, userAgent);
    }

    /** Redirect-flow counterpart: exchanges the OAuth authorization code Google handed back after
     * the full-page redirect, then resolves the identity through the exact same "must already be
     * linked" policy as {@link #loginWithGoogle} — Google never creates a Nukkad account either way. */
    @Transactional
    public AuthResponse loginWithGoogleAuthCode(String code, String redirectUri, String ip, String userAgent) {
        return authenticateGoogleIdentity(googleTokenVerifier.exchangeAuthorizationCode(code, redirectUri), ip, userAgent);
    }

    private AuthResponse authenticateGoogleIdentity(GoogleTokenVerifier.GoogleIdentity identity, String ip, String userAgent) {
        String email = identity.email().toLowerCase().trim();

        User user = userRepository.findByGoogleSubject(identity.subject())
                .orElseGet(() -> {
                    if (userRepository.existsByEmail(email)) {
                        throw new GoogleAccountNotLinkedException(
                                "This Nukkad account is not connected to Google yet. Sign in with your email and "
                                        + "password, then connect Google from Security settings.");
                    }
                    throw new GoogleAccountNotFoundException(
                            "Your Google account isn't connected to a Nukkad account yet. Please create a Nukkad account first.");
                });

        userRepository.touchLastActiveAt(user.getId(), Instant.now());
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        return issueAuthResponse(user, ip, userAgent);
    }

    @Transactional
    public void linkGoogleAccount(String userId, String rawIdToken) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(rawIdToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        if (!identity.email().equalsIgnoreCase(user.getEmail())) {
            throw new GoogleEmailMismatchException(
                    "Connect the Google account that uses the same email address as your Nukkad account.");
        }
        userRepository.findByGoogleSubject(identity.subject()).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new ConflictException("This Google account is already linked to a different Nukkad account.");
            }
        });

        user.setGoogleSubject(identity.subject());
        userRepository.save(user);
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
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), rawToken);
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

    private AuthResponse issueAuthResponse(User user, String ip, String userAgent) {
        RefreshToken refreshToken = issueRefreshToken(user.getId(), ip, userAgent);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user));
        return new AuthResponse(userMapper.toDto(user), accessToken, refreshToken.rawTokenTransient, jwtService.getAccessExpirationSeconds());
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
