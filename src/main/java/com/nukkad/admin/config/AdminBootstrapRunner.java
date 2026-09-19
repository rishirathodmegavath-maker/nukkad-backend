package com.nukkad.admin.config;

import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Establishes the very first platform Admin, on demand and only on demand — this never runs
 * unless both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD are explicitly set (see
 * .env.example). No default credential ever exists. The password is only ever hashed and never
 * logged, in any outcome.
 *
 * <p>Three outcomes, each safe to hit on every restart:
 * <ul>
 *   <li>Email not found — creates a fresh ACTIVE admin account (this can only happen once; every
 *       later run finds the account and falls into one of the two branches below).</li>
 *   <li>Email found, already ADMIN — no-op. Nothing is re-written: not the password, not the
 *       profile, not any timestamp.</li>
 *   <li>Email found, not yet ADMIN — promoted only if ADMIN_BOOTSTRAP_ALLOW_PROMOTION=true is also
 *       set; otherwise this is a no-op with a warning. An existing account is never silently
 *       turned into an admin — that would let anyone who already has (or later registers) that
 *       exact email inherit admin access the moment an operator sets the bootstrap email without
 *       realizing the account already exists.</li>
 * </ul>
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${nukkad.admin.bootstrap-email:}")
    private String bootstrapEmail;

    @Value("${nukkad.admin.bootstrap-password:}")
    private String bootstrapPassword;

    @Value("${nukkad.admin.bootstrap-allow-promotion:false}")
    private boolean allowPromotion;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank() || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return;
        }
        String email = bootstrapEmail.toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = User.builder()
                    .name("Admin")
                    .email(email)
                    .passwordHash(passwordEncoder.encode(bootstrapPassword))
                    .emailVerified(true)
                    .onboardingCompleted(true)
                    .status(AccountStatus.ACTIVE)
                    .securityRoles(new HashSet<>(Set.of(SecurityRole.USER, SecurityRole.ADMIN)))
                    .build();
            userRepository.saveAndFlush(user);
            log.info("Admin bootstrap: admin account bootstrapped successfully.");
            return;
        }

        if (user.getSecurityRoles().contains(SecurityRole.ADMIN)) {
            log.info("Admin bootstrap: existing admin detected.");
            return;
        }

        if (!allowPromotion) {
            log.warn("Admin bootstrap: an account already exists for the configured bootstrap email but does "
                    + "not have the ADMIN role. No role was changed. Set ADMIN_BOOTSTRAP_ALLOW_PROMOTION=true "
                    + "if you intend to promote this specific existing account.");
            return;
        }

        user.getSecurityRoles().add(SecurityRole.ADMIN);
        userRepository.save(user);
        log.info("Admin bootstrap: existing account promoted to admin successfully.");
    }
}
