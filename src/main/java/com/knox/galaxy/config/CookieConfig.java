package com.knox.galaxy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Pushes {@code galaxy.cookie.secure} into {@link CookieUtil}'s static state at
 * startup.
 *
 * <p>Done this way, rather than injecting the value where the cookies are
 * actually built, because CookieUtil is a static utility called straight from
 * AuthController — turning it into a bean would ripple through more code than
 * this single flag justifies. The write happens once, during context
 * initialisation, before any request can be served.
 */
@Configuration
public class CookieConfig {

    private static final Logger log = LoggerFactory.getLogger(CookieConfig.class);

    public CookieConfig(@Value("${galaxy.cookie.secure:true}") boolean secure) {
        CookieUtil.setSecure(secure);
        if (!secure) {
            log.warn("galaxy.cookie.secure=false — the refresh and CSRF cookies will be sent "
                    + "over plain HTTP in cleartext, readable by anything on the network path. "
                    + "This is only correct for a deployment with no TLS available (e.g. a bare "
                    + "IP with no domain). Set it back to true the moment a certificate exists.");
        }
    }
}
