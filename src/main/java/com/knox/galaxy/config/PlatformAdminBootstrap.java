package com.knox.galaxy.config;

import com.knox.galaxy.model.PlatformUser;
import com.knox.galaxy.repository.PlatformUserRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the first KNOX staff account, since there is no other way in: the
 * only endpoint that could create one is itself PLATFORM_ADMIN-only.
 *
 * <p>Runs only when both env vars are set, and only when the table is empty.
 * It never updates an existing account — a redeploy must not silently reset
 * someone's password back to whatever is in the environment.
 */
@Configuration
public class PlatformAdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    @Bean
    public ApplicationRunner seedPlatformAdmin(
            PlatformUserRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${galaxy.platform.bootstrap-email:}") String email,
            @Value("${galaxy.platform.bootstrap-password:}") String password,
            @Value("${galaxy.platform.bootstrap-name:KNOX Admin}") String fullName) {

        return args -> {
            if (email.isBlank() || password.isBlank()) {
                return;
            }
            TenantContext.clear();
            if (repository.count() > 0) {
                log.info("Platform admin bootstrap skipped: knox.platform_users is not empty");
                return;
            }
            PlatformUser user = new PlatformUser();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setActive(true);
            repository.save(user);
            log.info("Bootstrapped first KNOX platform admin: {}", email);
        };
    }
}
