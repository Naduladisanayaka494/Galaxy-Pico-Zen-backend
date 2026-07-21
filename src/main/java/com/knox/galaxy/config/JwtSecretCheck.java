package com.knox.galaxy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Warns loudly at boot if jwt.secret is still the value checked into
 * application.properties, or otherwise too weak. This does NOT hard-fail —
 * doing so would brick every existing dev environment the moment this
 * shipped, including this project's own, which has run on the checked-in
 * default for most of its life. A prominent startup warning is the honest
 * middle ground: visible, doesn't silently do nothing, doesn't break anyone
 * without warning.
 *
 * <p>jwt.secret is the entire trust boundary for both tenant and platform
 * tokens — anyone who reads this file's default value can forge a token for
 * any user or the platform admin.
 */
@Configuration
public class JwtSecretCheck {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretCheck.class);

    private static final String KNOWN_DEFAULT =
            "9a4f2c8d3b7a1e5f8g2h6j9k0l3m6n5o8p1q4r7s0t3u6v9w2x5y8z1a4b7c0d3e";

    @Bean
    public ApplicationRunner checkJwtSecret(@Value("${jwt.secret}") String secret) {
        return args -> {
            if (KNOWN_DEFAULT.equals(secret)) {
                log.warn("=====================================================================");
                log.warn("SECURITY: jwt.secret is still the default value checked into");
                log.warn("application.properties. Anyone who has read this repository can forge");
                log.warn("a valid token for any tenant user OR the platform admin. Set JWT_SECRET");
                log.warn("to a real random 32+ character value before this is reachable by anyone");
                log.warn("but you.");
                log.warn("=====================================================================");
            } else if (secret.length() < 32) {
                log.warn("SECURITY: jwt.secret is only {} characters. HS256 wants 32+ bytes of " +
                        "real entropy; a short secret is brute-forceable.", secret.length());
            }
        };
    }
}
