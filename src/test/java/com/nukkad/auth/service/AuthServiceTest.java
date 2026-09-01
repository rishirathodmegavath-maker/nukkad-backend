package com.nukkad.auth.service;

import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.RegisterRequest;
import com.nukkad.auth.dto.RegisterResponse;
import com.nukkad.auth.entity.EmailVerificationToken;
import com.nukkad.auth.repository.EmailVerificationTokenRepository;
import com.nukkad.auth.repository.PasswordResetTokenRepository;
import com.nukkad.auth.repository.RefreshTokenRepository;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.email.EmailService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.EmailNotVerifiedException;
import com.nukkad.common.exception.GoogleAccountNotFoundException;
import com.nukkad.common.exception.GoogleAccountNotLinkedException;
import com.nukkad.common.exception.GoogleEmailMismatchException;
import com.nukkad.security.JwtService;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditService auditService;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private EmailService emailService;

    private final UserMapper userMapper = new UserMapper();

    private AuthService service() {
        return new AuthService(userRepository, refreshTokenRepository, passwordResetTokenRepository,
                emailVerificationTokenRepository, passwordEncoder, jwtService, userMapper, auditService,
                googleTokenVerifier, emailService, mock(PlatformTransactionManager.class));
    }

    private User user(String id, String email, boolean verified, String googleSubject) {
        return User.builder().id(id).name("Test User").email(email).passwordHash("hashed")
                .emailVerified(verified).googleSubject(googleSubject)
                .securityRoles(new HashSet<>(Set.of(SecurityRole.USER))).build();
    }

    private void stubRefreshTokenSaveEcho() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- register ----

    @Test
    void registerCreatesUnverifiedUserAndSendsVerificationEmail() {
        when(userRepository.existsByEmail("new@nukkad.test")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u1");
            return u;
        });
        when(jwtService.generateOpaqueToken()).thenReturn("raw-token");
        when(jwtService.hashOpaqueToken("raw-token")).thenReturn("hashed-token");

        RegisterResponse response = service().register(
                new RegisterRequest("New User", "new@nukkad.test", "Str0ng!Passw0rd"), "127.0.0.1", "agent");

        assertThat(response.email()).isEqualTo("new@nukkad.test");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertThat(savedUser.getValue().isEmailVerified()).isFalse();

        ArgumentCaptor<EmailVerificationToken> savedToken = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(savedToken.capture());
        assertThat(savedToken.getValue().getTokenHash()).isEqualTo("hashed-token");

        verify(emailService).sendVerificationEmail("new@nukkad.test", "New User", "raw-token");
    }

    @Test
    void registerWithDuplicateEmailIsRejected() {
        when(userRepository.existsByEmail("dup@nukkad.test")).thenReturn(true);
        assertThatThrownBy(() -> service().register(
                new RegisterRequest("Dup", "dup@nukkad.test", "Str0ng!Passw0rd"), "127.0.0.1", "agent"))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---- login ----

    @Test
    void loginRejectsUnverifiedAccount() {
        User u = user("u1", "unverified@nukkad.test", false, null);
        when(userRepository.findByEmail("unverified@nukkad.test")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Str0ng!Passw0rd", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service().login(
                new LoginRequest("unverified@nukkad.test", "Str0ng!Passw0rd"), "127.0.0.1", "agent"))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginSucceedsForVerifiedAccount() {
        User u = user("u1", "verified@nukkad.test", true, null);
        when(userRepository.findByEmail("verified@nukkad.test")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Str0ng!Passw0rd", "hashed")).thenReturn(true);
        when(jwtService.generateOpaqueToken()).thenReturn("raw-refresh");
        when(jwtService.hashOpaqueToken("raw-refresh")).thenReturn("hashed-refresh");
        when(jwtService.issueAccessToken(any(), any(), any())).thenReturn("access-token");
        stubRefreshTokenSaveEcho();

        var response = service().login(new LoginRequest("verified@nukkad.test", "Str0ng!Passw0rd"), "127.0.0.1", "agent");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
    }

    @Test
    void loginRejectsWrongPassword() {
        User u = user("u1", "verified@nukkad.test", true, null);
        when(userRepository.findByEmail("verified@nukkad.test")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service().login(new LoginRequest("verified@nukkad.test", "wrong"), "127.0.0.1", "agent"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---- verifyEmail ----

    @Test
    void verifyEmailActivatesAccountAndConsumesToken() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id("t1").userId("u1").tokenHash("hashed-token").expiresAt(Instant.now().plusSeconds(3600)).build();
        User u = user("u1", "pending@nukkad.test", false, null);
        when(jwtService.hashOpaqueToken("raw")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        service().verifyEmail("raw");

        assertThat(u.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(u);
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id("t1").userId("u1").tokenHash("hashed-token").expiresAt(Instant.now().minusSeconds(1)).build();
        when(jwtService.hashOpaqueToken("raw")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().verifyEmail("raw")).isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailRejectsAlreadyUsedToken() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id("t1").userId("u1").tokenHash("hashed-token")
                .expiresAt(Instant.now().plusSeconds(3600)).usedAt(Instant.now().minusSeconds(60)).build();
        when(jwtService.hashOpaqueToken("raw")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().verifyEmail("raw")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        when(jwtService.hashOpaqueToken("raw")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verifyEmail("raw")).isInstanceOf(BadRequestException.class);
    }

    // ---- Google login ----

    @Test
    void googleLoginSucceedsWhenAlreadyLinked() {
        User u = user("u1", "linked@nukkad.test", true, "google-sub-1");
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-1", "linked@nukkad.test", "Linked User", null));
        when(userRepository.findByGoogleSubject("google-sub-1")).thenReturn(Optional.of(u));
        when(jwtService.generateOpaqueToken()).thenReturn("raw-refresh");
        when(jwtService.hashOpaqueToken("raw-refresh")).thenReturn("hashed-refresh");
        when(jwtService.issueAccessToken(any(), any(), any())).thenReturn("access-token");
        stubRefreshTokenSaveEcho();

        var response = service().loginWithGoogle("id-token", "127.0.0.1", "agent");

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void googleLoginRejectsWhenNoAccountExists() {
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-2", "nobody@nukkad.test", "Nobody", null));
        when(userRepository.findByGoogleSubject("google-sub-2")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("nobody@nukkad.test")).thenReturn(false);

        assertThatThrownBy(() -> service().loginWithGoogle("id-token", "127.0.0.1", "agent"))
                .isInstanceOf(GoogleAccountNotFoundException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void googleLoginRejectsWhenAccountExistsButNotLinked() {
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-3", "existing@nukkad.test", "Existing", null));
        when(userRepository.findByGoogleSubject("google-sub-3")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("existing@nukkad.test")).thenReturn(true);

        assertThatThrownBy(() -> service().loginWithGoogle("id-token", "127.0.0.1", "agent"))
                .isInstanceOf(GoogleAccountNotLinkedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---- Google linking ----

    @Test
    void linkGoogleAccountSucceedsWhenEmailMatches() {
        User u = user("u1", "me@nukkad.test", true, null);
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-4", "me@nukkad.test", "Me", null));
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findByGoogleSubject("google-sub-4")).thenReturn(Optional.empty());

        service().linkGoogleAccount("u1", "id-token");

        assertThat(u.getGoogleSubject()).isEqualTo("google-sub-4");
        verify(userRepository).save(u);
    }

    @Test
    void linkGoogleAccountRejectsEmailMismatch() {
        User u = user("u1", "me@nukkad.test", true, null);
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-5", "someoneelse@nukkad.test", "Someone Else", null));
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service().linkGoogleAccount("u1", "id-token"))
                .isInstanceOf(GoogleEmailMismatchException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void linkGoogleAccountRejectsWhenSubjectAlreadyLinkedToDifferentUser() {
        User u = user("u1", "me@nukkad.test", true, null);
        User other = user("u2", "other@nukkad.test", true, "google-sub-6");
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-6", "me@nukkad.test", "Me", null));
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findByGoogleSubject("google-sub-6")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service().linkGoogleAccount("u1", "id-token"))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(u);
    }
}
