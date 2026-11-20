package com.openroots.config;

import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first platform administrator from configuration
 * ({@code OPENROOTS_BOOTSTRAP_ADMIN_EMAIL} / {@code OPENROOTS_BOOTSTRAP_ADMIN_PASSWORD}).
 *
 * <p>Runs on every startup but only acts while no ADMIN exists, so it is safe to leave the
 * email configured. If the configured email already belongs to a registered account it is
 * <em>not</em> promoted: otherwise anyone who registered that address first would inherit
 * admin rights.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${openroots.bootstrap.admin.email:}") String email,
            @Value("${openroots.bootstrap.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByPlatformRole(PlatformRole.ADMIN)) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No platform administrator exists. Set OPENROOTS_BOOTSTRAP_ADMIN_EMAIL and "
                    + "OPENROOTS_BOOTSTRAP_ADMIN_PASSWORD to create one.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("Bootstrap admin not created: OPENROOTS_BOOTSTRAP_ADMIN_PASSWORD must be at least {} characters.",
                    MIN_PASSWORD_LENGTH);
            return;
        }
        if (userRepository.findByEmail(email).isPresent()) {
            log.error("Bootstrap admin not created: {} is already registered as a normal account. "
                    + "It is not promoted automatically. Choose a different bootstrap email.", email);
            return;
        }
        userRepository.save(new UserEntity(email, passwordEncoder.encode(password), PlatformRole.ADMIN));
        log.info("Created bootstrap platform administrator {}. Remove OPENROOTS_BOOTSTRAP_ADMIN_PASSWORD "
                + "from the environment after the first login.", email);
    }
}
