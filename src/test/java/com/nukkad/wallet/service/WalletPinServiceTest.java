package com.nukkad.wallet.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.ApiException;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.wallet.dto.WalletPinStatusDto;
import com.nukkad.wallet.dto.WalletUnlockDto;
import com.nukkad.wallet.entity.WalletPin;
import com.nukkad.wallet.repository.WalletPinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPinServiceTest {

    private static final String USER = "user-1";
    private static final String PIN = "482913";
    private static final String OTHER_PIN = "718293";
    private static final String PASSWORD = "Sup3r-Secret!";
    private static final int TOKEN_VERSION = 3;
    private static final String IP = "203.0.113.9";

    @Mock private WalletPinRepository pins;
    @Mock private UserRepository users;
    @Mock private AuditService audit;

    // Real hashing and real crypto, so these tests prove the actual PIN round trip rather than a mock of it.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final WalletPinCrypto crypto = new WalletPinCrypto("test-secret-test-secret-test-secret-0123456789", Clock.systemUTC());
    private WalletPinService service;

    @BeforeEach
    void setUp() {
        service = new WalletPinService(pins, users, encoder, crypto, audit);
    }

    private WalletPin pinRow(String pin) {
        return WalletPin.builder().userId(USER).pinHash(encoder.encode(crypto.pepperedPin(USER, pin))).build();
    }

    private void rowExists(WalletPin row) {
        when(pins.findByUserIdForUpdate(USER)).thenReturn(Optional.of(row));
    }

    private void accountPasswordIs(String password) {
        User user = User.builder().passwordHash(encoder.encode(password)).build();
        when(users.findById(USER)).thenReturn(Optional.of(user));
    }

    private static void assertApiError(Throwable thrown, HttpStatus status, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getErrorCode()).isEqualTo(code);
        });
    }

    // ---- create ----

    @Test
    void setPin_storesOnlyAHashAndUnlocksTheWalletImmediately() {
        accountPasswordIs(PASSWORD);
        when(pins.existsById(USER)).thenReturn(false);
        when(pins.saveAndFlush(any(WalletPin.class))).thenAnswer(inv -> inv.getArgument(0));

        WalletUnlockDto result = service.setPin(USER, TOKEN_VERSION, PIN, PASSWORD, IP);

        ArgumentCaptor<WalletPin> saved = ArgumentCaptor.forClass(WalletPin.class);
        verify(pins).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPinHash()).doesNotContain(PIN);
        assertThat(encoder.matches(crypto.pepperedPin(USER, PIN), saved.getValue().getPinHash())).isTrue();
        assertThat(crypto.isValidUnlockToken(result.unlockToken(), USER, TOKEN_VERSION, saved.getValue().getPinVersion())).isTrue();
        verify(audit).log(USER, AuditAction.WALLET_PIN_SET, "WalletPin", USER, IP);
    }

    @Test
    void setPin_refusesAWrongAccountPassword() {
        accountPasswordIs(PASSWORD);

        assertThatThrownBy(() -> service.setPin(USER, TOKEN_VERSION, PIN, "not-the-password", IP))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PASSWORD_INVALID"));
        verify(pins, never()).saveAndFlush(any());
    }

    @Test
    void setPin_cannotOverwriteAnExistingPin() {
        accountPasswordIs(PASSWORD);
        when(pins.existsById(USER)).thenReturn(true);

        assertThatThrownBy(() -> service.setPin(USER, TOKEN_VERSION, PIN, PASSWORD, IP))
                .isInstanceOf(ConflictException.class);
        verify(pins, never()).saveAndFlush(any());
    }

    @Test
    void setPin_refusesAnEasyToGuessPin() {
        accountPasswordIs(PASSWORD);
        when(pins.existsById(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.setPin(USER, TOKEN_VERSION, "123456", PASSWORD, IP))
                .isInstanceOf(BadRequestException.class);
        verify(pins, never()).saveAndFlush(any());
    }

    // ---- verify ----

    @Test
    void verify_correctPinIssuesAnUnlockTokenAndClearsEarlierFailures() {
        WalletPin row = pinRow(PIN);
        row.setFailedAttempts(3);
        rowExists(row);

        WalletUnlockDto result = service.verify(USER, TOKEN_VERSION, PIN, IP);

        assertThat(crypto.isValidUnlockToken(result.unlockToken(), USER, TOKEN_VERSION, row.getPinVersion())).isTrue();
        assertThat(row.getFailedAttempts()).isZero();
        verify(pins).save(row);
    }

    @Test
    void verify_wrongPinIsCountedAndTheCallerToldHowManyAttemptsRemain() {
        WalletPin row = pinRow(PIN);
        rowExists(row);

        assertThatThrownBy(() -> service.verify(USER, TOKEN_VERSION, OTHER_PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PIN_INVALID"))
                .hasMessageContaining("4 attempts left");

        assertThat(row.getFailedAttempts()).isEqualTo(1);
        verify(pins).save(row);
    }

    @Test
    void verify_lastAttemptBeforeALockSaysSo() {
        WalletPin row = pinRow(PIN);
        row.setFailedAttempts(3);
        rowExists(row);

        assertThatThrownBy(() -> service.verify(USER, TOKEN_VERSION, OTHER_PIN, IP))
                .hasMessageContaining("1 attempt left");
    }

    @Test
    void verify_theFifthWrongPinLocksTheWalletForFifteenMinutesAndEndsOpenSessions() {
        WalletPin row = pinRow(PIN);
        row.setFailedAttempts(4);
        int versionBefore = row.getPinVersion();
        rowExists(row);
        Instant before = Instant.now();

        assertThatThrownBy(() -> service.verify(USER, TOKEN_VERSION, OTHER_PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.TOO_MANY_REQUESTS, "WALLET_PIN_LOCKED"));

        assertThat(row.getFailedAttempts()).isEqualTo(5);
        assertThat(row.getLockedUntil()).isBetween(before.plusSeconds(15 * 60 - 5), Instant.now().plusSeconds(15 * 60 + 5));
        assertThat(row.getPinVersion()).isEqualTo(versionBefore + 1);
        verify(pins).save(row);
        verify(audit).log(USER, AuditAction.WALLET_PIN_LOCKED, "WalletPin", USER, IP);
    }

    @Test
    void verify_whileLockedEvenTheCorrectPinIsRefusedAndNothingIsCounted() {
        WalletPin row = pinRow(PIN);
        row.setFailedAttempts(5);
        row.setLockedUntil(Instant.now().plusSeconds(600));
        rowExists(row);

        assertThatThrownBy(() -> service.verify(USER, TOKEN_VERSION, PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.TOO_MANY_REQUESTS, "WALLET_PIN_LOCKED"));

        assertThat(row.getFailedAttempts()).isEqualTo(5);
        verify(pins, never()).save(any());
    }

    @Test
    void verify_afterTheLockExpiresTheCorrectPinWorksAgain() {
        WalletPin row = pinRow(PIN);
        row.setFailedAttempts(5);
        row.setLockedUntil(Instant.now().minusSeconds(1));
        rowExists(row);

        WalletUnlockDto result = service.verify(USER, TOKEN_VERSION, PIN, IP);

        assertThat(result.unlockToken()).isNotBlank();
        assertThat(row.getFailedAttempts()).isZero();
        assertThat(row.getLockedUntil()).isNull();
    }

    @Test
    void verify_failuresAfterALockCarryOverSoLaterLocksGetLonger() {
        // Five wrong PINs put the count at 5; it must NOT reset when the lock lapses, otherwise an
        // attacker would get a fresh five guesses every 15 minutes forever.
        assertThat(WalletPinService.lockSecondsFor(4)).isZero();
        assertThat(WalletPinService.lockSecondsFor(5)).isEqualTo(15 * 60);
        assertThat(WalletPinService.lockSecondsFor(6)).isZero();
        assertThat(WalletPinService.lockSecondsFor(10)).isEqualTo(60 * 60);
        assertThat(WalletPinService.lockSecondsFor(15)).isEqualTo(24 * 60 * 60);
        assertThat(WalletPinService.lockSecondsFor(40)).isEqualTo(24 * 60 * 60);
    }

    @Test
    void verify_withNoPinSetSaysSo() {
        when(pins.findByUserIdForUpdate(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(USER, TOKEN_VERSION, PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PIN_NOT_SET"));
    }

    // ---- change ----

    @Test
    void changePin_replacesThePinAndInvalidatesTheOldWalletSession() {
        WalletPin row = pinRow(PIN);
        rowExists(row);
        String oldToken = crypto.issueUnlockToken(USER, TOKEN_VERSION, row.getPinVersion());

        WalletUnlockDto result = service.changePin(USER, TOKEN_VERSION, PIN, OTHER_PIN, IP);

        assertThat(encoder.matches(crypto.pepperedPin(USER, OTHER_PIN), row.getPinHash())).isTrue();
        assertThat(encoder.matches(crypto.pepperedPin(USER, PIN), row.getPinHash())).isFalse();
        assertThat(crypto.isValidUnlockToken(oldToken, USER, TOKEN_VERSION, row.getPinVersion())).isFalse();
        assertThat(crypto.isValidUnlockToken(result.unlockToken(), USER, TOKEN_VERSION, row.getPinVersion())).isTrue();
        verify(audit).log(USER, AuditAction.WALLET_PIN_CHANGED, "WalletPin", USER, IP);
    }

    @Test
    void changePin_aWrongCurrentPinCountsAsAFailedAttemptAndChangesNothing() {
        WalletPin row = pinRow(PIN);
        String hashBefore = row.getPinHash();
        rowExists(row);

        assertThatThrownBy(() -> service.changePin(USER, TOKEN_VERSION, "999123", OTHER_PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PIN_INVALID"));

        assertThat(row.getFailedAttempts()).isEqualTo(1);
        assertThat(row.getPinHash()).isEqualTo(hashBefore);
    }

    @Test
    void changePin_refusesTheSamePin() {
        rowExists(pinRow(PIN));

        assertThatThrownBy(() -> service.changePin(USER, TOKEN_VERSION, PIN, PIN, IP))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changePin_aWeakNewPinIsRefusedWithoutSpendingAnAttempt() {
        assertThatThrownBy(() -> service.changePin(USER, TOKEN_VERSION, PIN, "111111", IP))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(pins);
    }

    @Test
    void changePin_isRefusedWhileLocked() {
        WalletPin row = pinRow(PIN);
        row.setLockedUntil(Instant.now().plusSeconds(300));
        rowExists(row);

        assertThatThrownBy(() -> service.changePin(USER, TOKEN_VERSION, PIN, OTHER_PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.TOO_MANY_REQUESTS, "WALLET_PIN_LOCKED"));
    }

    // ---- reset (forgot PIN) ----

    @Test
    void resetPin_usesTheAccountPasswordClearsTheLockoutAndEndsOldSessions() {
        WalletPin row = pinRow(PIN);
        row.setFailedAttempts(7);
        row.setLockedUntil(Instant.now().plusSeconds(3000));
        int versionBefore = row.getPinVersion();
        accountPasswordIs(PASSWORD);
        rowExists(row);

        WalletUnlockDto result = service.resetPin(USER, TOKEN_VERSION, PASSWORD, OTHER_PIN, IP);

        assertThat(encoder.matches(crypto.pepperedPin(USER, OTHER_PIN), row.getPinHash())).isTrue();
        assertThat(row.getFailedAttempts()).isZero();
        assertThat(row.getLockedUntil()).isNull();
        assertThat(row.getPinVersion()).isEqualTo(versionBefore + 1);
        assertThat(crypto.isValidUnlockToken(result.unlockToken(), USER, TOKEN_VERSION, row.getPinVersion())).isTrue();
        verify(audit).log(USER, AuditAction.WALLET_PIN_RESET, "WalletPin", USER, IP);
    }

    @Test
    void resetPin_refusesAWrongAccountPasswordAndChangesNothing() {
        accountPasswordIs(PASSWORD);

        assertThatThrownBy(() -> service.resetPin(USER, TOKEN_VERSION, "not-the-password", OTHER_PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PASSWORD_INVALID"));
        verify(pins, never()).save(any());
    }

    @Test
    void resetPin_needsAnExistingPin() {
        accountPasswordIs(PASSWORD);
        when(pins.findByUserIdForUpdate(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPin(USER, TOKEN_VERSION, PASSWORD, OTHER_PIN, IP))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PIN_NOT_SET"));
    }

    // ---- the gate in front of every wallet request ----

    private static AuthenticatedUser principal() {
        return new AuthenticatedUser(USER, "a@b.co", Set.of("USER"), TOKEN_VERSION);
    }

    @Test
    void assertUnlocked_passesWithAFreshTokenForThisUser() {
        WalletPin row = pinRow(PIN);
        when(pins.findById(USER)).thenReturn(Optional.of(row));
        String token = crypto.issueUnlockToken(USER, TOKEN_VERSION, row.getPinVersion());

        service.assertUnlocked(principal(), token);
    }

    @Test
    void assertUnlocked_withNoPinSetAsksToCreateOne() {
        when(pins.findById(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertUnlocked(principal(), "anything"))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_PIN_NOT_SET"));
    }

    @Test
    void assertUnlocked_refusesAMissingOrInvalidToken() {
        when(pins.findById(USER)).thenReturn(Optional.of(pinRow(PIN)));

        for (String token : new String[] {null, "", "not-a-token", "a.b"}) {
            assertThatThrownBy(() -> service.assertUnlocked(principal(), token))
                    .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_LOCKED"));
        }
    }

    @Test
    void assertUnlocked_refusesATokenFromBeforeThePinChanged() {
        WalletPin row = pinRow(PIN);
        when(pins.findById(USER)).thenReturn(Optional.of(row));
        String staleToken = crypto.issueUnlockToken(USER, TOKEN_VERSION, row.getPinVersion());
        row.setPinVersion(row.getPinVersion() + 1);

        assertThatThrownBy(() -> service.assertUnlocked(principal(), staleToken))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_LOCKED"));
    }

    @Test
    void assertUnlocked_refusesATokenWhenTheSessionGenerationChanged() {
        WalletPin row = pinRow(PIN);
        when(pins.findById(USER)).thenReturn(Optional.of(row));
        String token = crypto.issueUnlockToken(USER, TOKEN_VERSION - 1, row.getPinVersion());

        assertThatThrownBy(() -> service.assertUnlocked(principal(), token))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_LOCKED"));
    }

    @Test
    void assertUnlocked_refusesEvenAValidTokenWhileLocked() {
        WalletPin row = pinRow(PIN);
        String token = crypto.issueUnlockToken(USER, TOKEN_VERSION, row.getPinVersion());
        row.setLockedUntil(Instant.now().plusSeconds(600));
        when(pins.findById(USER)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.assertUnlocked(principal(), token))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_LOCKED"));
    }

    @Test
    void assertUnlocked_refusesAnotherUsersToken() {
        WalletPin row = pinRow(PIN);
        when(pins.findById(USER)).thenReturn(Optional.of(row));
        String othersToken = crypto.issueUnlockToken("user-2", TOKEN_VERSION, row.getPinVersion());

        assertThatThrownBy(() -> service.assertUnlocked(principal(), othersToken))
                .satisfies(e -> assertApiError(e, HttpStatus.FORBIDDEN, "WALLET_LOCKED"));
    }

    // ---- status ----

    @Test
    void status_reportsWhetherAPinExistsAndAnyRemainingLock() {
        when(pins.findById(USER)).thenReturn(Optional.empty());
        assertThat(service.status(USER)).isEqualTo(new WalletPinStatusDto(false, 0));

        WalletPin row = pinRow(PIN);
        row.setLockedUntil(Instant.now().plusSeconds(120));
        when(pins.findById(USER)).thenReturn(Optional.of(row));
        WalletPinStatusDto locked = service.status(USER);
        assertThat(locked.hasPin()).isTrue();
        assertThat(locked.lockedForSeconds()).isBetween(110L, 120L);
    }

    @Test
    void theAuditTrailRecordsThatAPinWasSetButNeverCarriesDetails() {
        accountPasswordIs(PASSWORD);
        when(pins.existsById(USER)).thenReturn(false);
        when(pins.saveAndFlush(any(WalletPin.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setPin(USER, TOKEN_VERSION, PIN, PASSWORD, IP);

        // Only the five-argument overload (no free-form details map) is ever used for PIN events, so
        // there is no field a PIN could leak into.
        verify(audit).log(eq(USER), eq(AuditAction.WALLET_PIN_SET), anyString(), anyString(), anyString());
        verify(audit, never()).log(anyString(), any(), anyString(), anyString(), anyString(), any());
    }
}
