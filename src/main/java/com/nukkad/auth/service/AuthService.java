package com.nukkad.auth.service;

import com.nukkad.auth.dto.AdminAuthResponse;
import com.nukkad.auth.dto.AdminIdentity;
import com.nukkad.auth.dto.AuthResponse;
import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.RegisterRequest;
import com.nukkad.auth.dto.RegisterResponse;
import com.nukkad.auth.entity.EmailVerificationToken;
import com.nukkad.auth.entity.PasswordResetToken;
import com.nukkad.auth.entity.RefreshToken;
import com.nukkad.auth.entity.ResetAudience;
import com.nukkad.auth.repository.EmailVerificationTokenRepository;
import com.nukkad.auth.repository.PasswordResetTokenRepository;
import com.nukkad.auth.repository.RefreshTokenRepository;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.email.EmailService;
import com.nukkad.common.exception.AccountDisabledException;
import com.nukkad.common.exception.AccountSuspendedException;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.EmailNotVerifiedException;
import com.nukkad.common.exception.GoogleAccountNotFoundException;
import com.nukkad.common.exception.GoogleAccountNotLinkedException;
import com.nukkad.common.exception.GoogleEmailMismatchException;
import com.nukkad.common.exception.UnauthorizedException;
import com.nukkad.security.JwtService;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    // Shorter than the member link (1h): this one unlocks the account that can see and act on everything.
    private static final long ADMIN_PASSWORD_RESET_EXPIRY_SECONDS = 30L * 60;

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

    /** Testing-only escape hatch while production has no working SMTP: skips the verification
     * email/gate entirely so signup+login work without any mail server. Flip back to true (the
     * default) once real SMTP is configured — see nukkad.mail.* / SMTP_* env vars. */
    @Value("${nukkad.auth.require-email-verification:true}")
    private boolean requireEmailVerification;

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
                .emailVerified(!requireEmailVerification)
                .securityRoles(new HashSet<>(Set.of(SecurityRole.USER)))
                .build();
        user = userRepository.saveAndFlush(user);
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        if (requireEmailVerification) {
            issueVerificationEmail(user);
            return new RegisterResponse(user.getEmail(), "Account created. Check your email to verify your address before signing in.", false);
        }
        return new RegisterResponse(user.getEmail(), "Account created. You can log in now.", true);
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
        requireActiveAccount(user);
        requireMemberAccount(user);
        userRepository.touchLastActiveAt(user.getId(), Instant.now());
        auditService.log(user.getId(), AuditAction.LOGIN, "User", user.getId(), ip);
        return issueAuthResponse(user, ip, userAgent);
    }

    /** Admin sign-in for the separate admin portal. Uses the same email + password as the account,
     *  but issues an admin-scoped session that the member application rejects. A non-admin account
     *  gets the same generic error as a wrong password, so this can't be used to discover admins. */
    @Transactional
    public AdminAuthResponse adminLogin(String email, String password, String ip, String userAgent) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())
                || !user.getSecurityRoles().contains(SecurityRole.ADMIN)) {
            throw new BadCredentialsException("Invalid email or password");
        }
        requireActiveAccount(user);
        userRepository.touchLastActiveAt(user.getId(), Instant.now());
        auditService.log(user.getId(), AuditAction.LOGIN, "AdminPortal", user.getId(), ip);
        RefreshToken refreshToken = issueRefreshToken(user.getId(), ip, userAgent);
        String accessToken = jwtService.issueAdminAccessToken(user.getId(), user.getEmail(), roleNames(user), user.getTokenVersion());
        return new AdminAuthResponse(toAdminIdentity(user), accessToken, refreshToken.rawTokenTransient, jwtService.getAccessExpirationSeconds());
    }

    @Transactional(readOnly = true)
    public AdminIdentity adminIdentity(String userId) {
        return toAdminIdentity(userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists")));
    }

    private AdminIdentity toAdminIdentity(User user) {
        return new AdminIdentity(user.getId(), user.getEmail(), user.getName());
    }

    /** Administrators only use the admin portal — they are deliberately not members of the
     *  application, so the member sign-in refuses them (after the password has been verified, so
     *  this doesn't reveal which emails are admins to someone without the password). */
    private void requireMemberAccount(User user) {
        if (user.getSecurityRoles().contains(SecurityRole.ADMIN)) {
            throw new com.nukkad.common.exception.ForbiddenException(
                    "Administrator accounts sign in at the admin portal, not here.");
        }
    }

    /** Blocks sign-in and token refresh for an Admin-suspended/disabled account. A currently-valid
     *  access token (15 min TTL) still works until it naturally expires — suspending a user revokes
     *  their refresh tokens immediately, so no new session can be started or renewed. */
    private void requireActiveAccount(User user) {
        switch (user.getStatus()) {
            case SUSPENDED -> throw new AccountSuspendedException("Your account has been suspended. Contact support for details.");
            case DISABLED -> throw new AccountDisabledException("Your account has been disabled.");
            case ACTIVE -> { /* no-op */ }
        }
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
     * linked" policy as {@link #loginWithGoogle} — Google never creates a BuildAdda account either way. */
    @Transactional
    public AuthResponse loginWithGoogleAuthCode(String code, String redirectUri, String ip, String userAgent) {
        return authenticateGoogleIdentity(googleTokenVerifier.exchangeAuthorizationCode(code, redirectUri), ip, userAgent);
    }

    private AuthResponse authenticateGoogleIdentity(GoogleTokenVerifier.GoogleIdentity identity, String ip, String userAgent) {
        String email = identity.email().toLowerCase().trim();

        User user = userRepository.findByGoogleSubject(identity.subject())
                .orElseGet(() -> {
                    User existingByEmail = userRepository.findByEmail(email).orElse(null);
                    if (existingByEmail == null) {
                        throw new GoogleAccountNotFoundException(
                                "Your Google account isn't connected to a BuildAdda account yet. Please create a BuildAdda account first.");
                    }
                    if (existingByEmail.getGoogleSubject() != null) {
                        // Already linked — just not to *this* Google identity. A real mismatch, not a
                        // migration gap: don't silently relink.
                        throw new GoogleAccountNotLinkedException(
                                "This BuildAdda account is not connected to Google yet. Sign in with your email and "
                                        + "password, then connect Google from Security settings.");
                    }
                    // One-time migration backfill: this account was created back when Google Sign-In
                    // auto-created/matched accounts by email alone and never recorded a Google subject
                    // at all (that's the exact gap this whole rework closed). Google's own token proves
                    // ownership of this verified email, so it's safe to complete the link now rather
                    // than lock out every pre-existing Google-only user who has no password to fall
                    // back on.
                    existingByEmail.setGoogleSubject(identity.subject());
                    return userRepository.save(existingByEmail);
                });

        requireActiveAccount(user);
        requireMemberAccount(user);
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
                    "Connect the Google account that uses the same email address as your BuildAdda account.");
        }
        userRepository.findByGoogleSubject(identity.subject()).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new ConflictException("This Google account is already linked to a different BuildAdda account.");
            }
        });

        user.setGoogleSubject(identity.subject());
        userRepository.save(user);
    }

    @Transactional
    public com.nukkad.auth.dto.RefreshTokenResponse refresh(String presentedRawToken, String ip, String userAgent) {
        return rotateRefreshToken(presentedRawToken, ip, userAgent, false);
    }

    @Transactional
    public com.nukkad.auth.dto.RefreshTokenResponse adminRefresh(String presentedRawToken, String ip, String userAgent) {
        return rotateRefreshToken(presentedRawToken, ip, userAgent, true);
    }

    /** {@code adminPortal} pins a refresh token to the audience it was issued for: an admin's token
     *  can only be renewed through the admin portal (yielding an admin-scoped access token) and a
     *  member's only through the member endpoint — otherwise the refresh endpoint would let an admin
     *  session quietly turn into a member session, or the reverse. */
    private com.nukkad.auth.dto.RefreshTokenResponse rotateRefreshToken(String presentedRawToken, String ip,
                                                                       String userAgent, boolean adminPortal) {
        String hash = jwtService.hashOpaqueToken(presentedRawToken);
        // Locked read: serializes concurrent refresh attempts on the same token row so at most one
        // can win the rotation below -- see the repository method's own doc comment.
        RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(hash)
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
        requireActiveAccount(user);
        if (user.getSecurityRoles().contains(SecurityRole.ADMIN) != adminPortal) {
            throw new UnauthorizedException("This session is not valid here. Please sign in again.");
        }

        RefreshToken rotated = issueRefreshToken(user.getId(), ip, userAgent);
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByTokenId(rotated.getId());
        refreshTokenRepository.save(existing);

        String accessToken = adminPortal
                ? jwtService.issueAdminAccessToken(user.getId(), user.getEmail(), roleNames(user), user.getTokenVersion())
                : jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user), user.getTokenVersion());
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

    /** Admin sign-out additionally bumps tokenVersion, unlike member {@link #logout}: revoking only
     *  the refresh token leaves the still-live access token fully usable against /api/admin/** for
     *  up to its remaining TTL (a captured/replayed admin session would otherwise survive its own
     *  "sign out"). Member logout deliberately does NOT do this -- a member may be logged in on
     *  several devices at once, and this would sign all of them out, not just this one; the admin
     *  portal is a single-operator control panel where that tradeoff is the safer default, matching
     *  the same tokenVersion-bump mechanism already used for suspend/role-change. */
    @Transactional
    public void adminLogout(String presentedRawToken) {
        String hash = jwtService.hashOpaqueToken(presentedRawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            userRepository.findById(token.getUserId()).ifPresent(user -> {
                user.setTokenVersion(user.getTokenVersion() + 1);
                userRepository.save(user);
            });
        });
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email.toLowerCase().trim())
                // Administrators recover through the admin portal's own flow (admin-audience token,
                // admin-host link); the member site refuses admin accounts, so a link to it is useless.
                .filter(user -> !user.getSecurityRoles().contains(SecurityRole.ADMIN))
                .ifPresent(user -> {
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
                // An admin-audience token must never be redeemable here: this path does not bump
                // tokenVersion, so it would leave the admin's still-live access token valid.
                .filter(token -> token.getAudience() == ResetAudience.MEMBER)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        revokeAllForUser(user.getId());
    }

    // ---- admin password recovery (separate from the member flow above) ----

    private boolean isActiveAdmin(User user) {
        return user.getStatus() == AccountStatus.ACTIVE && user.getSecurityRoles().contains(SecurityRole.ADMIN);
    }

    /**
     * Emails an admin a one-time reset link on the admin host. Behaves identically (and returns
     * nothing) whether or not the address belongs to an active administrator, so it can't be used to
     * discover which email is the admin's. Any earlier unused admin link is voided first, so only the
     * newest emailed link ever works.
     */
    @Transactional
    public void requestAdminPasswordReset(String email, String ip) {
        userRepository.findByEmail(email.toLowerCase().trim())
                .filter(this::isActiveAdmin)
                .ifPresent(user -> {
                    voidOutstandingAdminResetTokens(user.getId());
                    String rawToken = jwtService.generateOpaqueToken();
                    passwordResetTokenRepository.save(PasswordResetToken.builder()
                            .userId(user.getId())
                            .audience(ResetAudience.ADMIN)
                            .tokenHash(jwtService.hashOpaqueToken(rawToken))
                            .expiresAt(Instant.now().plusSeconds(ADMIN_PASSWORD_RESET_EXPIRY_SECONDS))
                            .build());
                    auditService.log(user.getId(), AuditAction.ADMIN_PASSWORD_RESET_REQUESTED, "AdminPortal", user.getId(), ip);
                    emailService.sendAdminPasswordResetEmail(user.getEmail(), user.getName(), rawToken);
                });
    }

    /**
     * Sets a new admin password from an emailed link. Beyond what the member flow does it also bumps
     * tokenVersion, so every access token issued before the reset dies immediately (not up to 15
     * minutes later) — the point of resetting is usually that someone else may hold the old session.
     */
    @Transactional
    public void confirmAdminPasswordReset(String rawToken, String newPassword, String ip) {
        // Row-locked: two simultaneous redemptions of one link must not both succeed.
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(jwtService.hashOpaqueToken(rawToken))
                .filter(PasswordResetToken::isUsable)
                .filter(token -> token.getAudience() == ResetAudience.ADMIN)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));

        User user = userRepository.findById(resetToken.getUserId())
                .filter(this::isActiveAdmin)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        voidOutstandingAdminResetTokens(user.getId());

        auditService.log(user.getId(), AuditAction.ADMIN_PASSWORD_RESET_COMPLETED, "AdminPortal", user.getId(), ip);
        revokeAllForUser(user.getId());
    }

    /** Signed-in admin changes their own password. The new password must differ from the current one
     *  (otherwise "changing" a leaked password to itself would look like a rotation), and every session
     *  — including this one — is ended, so the admin signs in again with the new password. */
    @Transactional
    public void adminChangePassword(String userId, String currentPassword, String newPassword, String ip) {
        User user = userRepository.findById(userId)
                .filter(this::isActiveAdmin)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Choose a password different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        voidOutstandingAdminResetTokens(userId);
        auditService.log(userId, AuditAction.ADMIN_PASSWORD_CHANGED, "AdminPortal", userId, ip);
        revokeAllForUser(userId);
    }

    private void voidOutstandingAdminResetTokens(String userId) {
        Instant now = Instant.now();
        passwordResetTokenRepository.findByUserIdAndAudienceAndUsedAtIsNull(userId, ResetAudience.ADMIN)
                .forEach(token -> {
                    token.setUsedAt(now);
                    passwordResetTokenRepository.save(token);
                });
    }

    /**
     * Runs in its own committed transaction (REQUIRES_NEW) so the revocation survives even when
     * the caller immediately throws afterward (e.g. refresh-token-reuse detection) — otherwise
     * Spring's default rollback-on-RuntimeException would undo this side effect along with it.
     */
    public void revokeAllForUser(String userId) {
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
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user), user.getTokenVersion());
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
