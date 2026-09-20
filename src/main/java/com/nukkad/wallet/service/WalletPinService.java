package com.nukkad.wallet.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.ApiException;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.UnauthorizedException;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.wallet.dto.WalletPinStatusDto;
import com.nukkad.wallet.dto.WalletUnlockDto;
import com.nukkad.wallet.entity.WalletPin;
import com.nukkad.wallet.repository.WalletPinRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * The PIN in front of the wallet: create, verify, change, reset, and the check that guards every
 * wallet request.
 *
 * <p><b>Lockout.</b> Wrong PINs are counted per account, in the database, under a row lock. Every
 * fifth consecutive wrong PIN locks the wallet: 15 minutes, then 1 hour, then 24 hours for each
 * further round. The count only returns to zero on a correct PIN or a reset, so an attacker never
 * gets more than five guesses per lock — at most a few dozen a day against a million combinations.
 *
 * <p><b>Transactions.</b> {@code verify} and {@code changePin} are {@code noRollbackFor =
 * ApiException}: they record a failed attempt and then <em>throw</em> to tell the caller. With the
 * default rollback-on-exception behaviour that very throw would undo the increment, and the lockout
 * would never trigger. Do not remove it.
 */
@Service
public class WalletPinService {

    static final int ATTEMPTS_PER_LOCK = 5;

    private final WalletPinRepository walletPinRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletPinCrypto crypto;
    private final AuditService auditService;

    public WalletPinService(WalletPinRepository walletPinRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            WalletPinCrypto crypto,
                            AuditService auditService) {
        this.walletPinRepository = walletPinRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public WalletPinStatusDto status(String userId) {
        Instant now = Instant.now();
        return walletPinRepository.findById(userId)
                .map(pin -> new WalletPinStatusDto(true, secondsLocked(pin, now)))
                .orElse(new WalletPinStatusDto(false, 0));
    }

    @Transactional
    public WalletUnlockDto setPin(String userId, int tokenVersion, String pin, String password, String ip) {
        requireAccountPassword(userId, password);
        if (walletPinRepository.existsById(userId)) {
            throw new ConflictException("You already have a wallet PIN. Change it, or use Forgot PIN to reset it.");
        }
        requireStrong(pin);
        WalletPin row = walletPinRepository.saveAndFlush(WalletPin.builder()
                .userId(userId)
                .pinHash(hash(userId, pin))
                .build());
        auditService.log(userId, AuditAction.WALLET_PIN_SET, "WalletPin", userId, ip);
        return unlockToken(userId, tokenVersion, row);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public WalletUnlockDto verify(String userId, int tokenVersion, String pin, String ip) {
        WalletPin row = lockRow(userId);
        Instant now = Instant.now();
        requireNotLocked(row, now);
        requireCorrectPin(row, pin, now, ip);
        return unlockToken(userId, tokenVersion, row);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public WalletUnlockDto changePin(String userId, int tokenVersion, String currentPin, String newPin, String ip) {
        // Checked first and without touching the attempt counter: whether a candidate PIN is "too
        // simple" says nothing about the PIN that is currently set.
        requireStrong(newPin);
        WalletPin row = lockRow(userId);
        Instant now = Instant.now();
        requireNotLocked(row, now);
        requireCorrectPin(row, currentPin, now, ip);
        if (currentPin.equals(newPin)) {
            throw new BadRequestException("Choose a PIN different from your current one.");
        }
        replacePin(row, userId, newPin);
        auditService.log(userId, AuditAction.WALLET_PIN_CHANGED, "WalletPin", userId, ip);
        return unlockToken(userId, tokenVersion, row);
    }

    /** "Forgot PIN": the account password stands in for the old PIN, and clears any lockout. */
    @Transactional
    public WalletUnlockDto resetPin(String userId, int tokenVersion, String password, String newPin, String ip) {
        requireAccountPassword(userId, password);
        requireStrong(newPin);
        WalletPin row = lockRow(userId);
        replacePin(row, userId, newPin);
        auditService.log(userId, AuditAction.WALLET_PIN_RESET, "WalletPin", userId, ip);
        return unlockToken(userId, tokenVersion, row);
    }

    /** The gate every wallet request passes through (see WalletUnlockInterceptor). */
    @Transactional(readOnly = true)
    public void assertUnlocked(AuthenticatedUser principal, String unlockToken) {
        WalletPin row = walletPinRepository.findById(principal.id())
                .orElseThrow(WalletPinService::pinNotSet);
        if (row.isLockedAt(Instant.now())
                || !crypto.isValidUnlockToken(unlockToken, principal.id(), principal.tokenVersion(), row.getPinVersion())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WALLET_LOCKED", "Enter your wallet PIN to continue.");
        }
    }

    // ---- internals ----

    private WalletPin lockRow(String userId) {
        return walletPinRepository.findByUserIdForUpdate(userId).orElseThrow(WalletPinService::pinNotSet);
    }

    private static ApiException pinNotSet() {
        return new ApiException(HttpStatus.FORBIDDEN, "WALLET_PIN_NOT_SET", "Create a wallet PIN to use your wallet.");
    }

    private void requireNotLocked(WalletPin row, Instant now) {
        if (row.isLockedAt(now)) {
            throw lockedException(secondsLocked(row, now));
        }
    }

    private void requireCorrectPin(WalletPin row, String pin, Instant now, String ip) {
        if (passwordEncoder.matches(crypto.pepperedPin(row.getUserId(), pin), row.getPinHash())) {
            if (row.getFailedAttempts() != 0 || row.getLockedUntil() != null) {
                row.setFailedAttempts(0);
                row.setLockedUntil(null);
                walletPinRepository.save(row);
            }
            return;
        }

        int failed = row.getFailedAttempts() + 1;
        row.setFailedAttempts(failed);
        long lockSeconds = lockSecondsFor(failed);
        if (lockSeconds > 0) {
            row.setLockedUntil(now.plusSeconds(lockSeconds));
            // Also ends any wallet session that is currently open.
            row.setPinVersion(row.getPinVersion() + 1);
            walletPinRepository.save(row);
            auditService.log(row.getUserId(), AuditAction.WALLET_PIN_LOCKED, "WalletPin", row.getUserId(), ip);
            throw lockedException(lockSeconds);
        }
        walletPinRepository.save(row);
        int left = ATTEMPTS_PER_LOCK - (failed % ATTEMPTS_PER_LOCK);
        throw new ApiException(HttpStatus.FORBIDDEN, "WALLET_PIN_INVALID",
                "Incorrect PIN. " + left + (left == 1 ? " attempt" : " attempts") + " left before the wallet locks.");
    }

    /** Every {@code ATTEMPTS_PER_LOCK}-th consecutive failure locks, for longer each round. */
    static long lockSecondsFor(int failedAttempts) {
        if (failedAttempts % ATTEMPTS_PER_LOCK != 0) return 0;
        return switch (failedAttempts / ATTEMPTS_PER_LOCK) {
            case 1 -> Duration.ofMinutes(15).toSeconds();
            case 2 -> Duration.ofHours(1).toSeconds();
            default -> Duration.ofHours(24).toSeconds();
        };
    }

    private static long secondsLocked(WalletPin row, Instant now) {
        return row.isLockedAt(now) ? Math.max(1, Duration.between(now, row.getLockedUntil()).toSeconds()) : 0;
    }

    private static ApiException lockedException(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        String wait = minutes >= 120 ? (minutes + 59) / 60 + " hours" : minutes == 1 ? "1 minute" : minutes + " minutes";
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "WALLET_PIN_LOCKED",
                "Too many incorrect PINs. Try again in " + wait + ", or use Forgot PIN.");
    }

    private void replacePin(WalletPin row, String userId, String newPin) {
        row.setPinHash(hash(userId, newPin));
        row.setPinVersion(row.getPinVersion() + 1);
        row.setFailedAttempts(0);
        row.setLockedUntil(null);
        walletPinRepository.save(row);
    }

    private void requireAccountPassword(String userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WALLET_PASSWORD_INVALID", "Your account password is incorrect.");
        }
    }

    private static void requireStrong(String pin) {
        if (WalletPinPolicy.isWeak(pin)) {
            throw new BadRequestException("Choose a PIN that is harder to guess — not repeated or sequential digits like 111111 or 123456.");
        }
    }

    private String hash(String userId, String pin) {
        return passwordEncoder.encode(crypto.pepperedPin(userId, pin));
    }

    private WalletUnlockDto unlockToken(String userId, int tokenVersion, WalletPin row) {
        return new WalletUnlockDto(
                crypto.issueUnlockToken(userId, tokenVersion, row.getPinVersion()), WalletPinCrypto.UNLOCK_TTL_SECONDS);
    }
}
