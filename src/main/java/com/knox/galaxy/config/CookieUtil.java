package com.knox.galaxy.config;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Builds the two auth cookies. SameSite=Strict requires the frontend and this
 * API to be served same-origin, which the deployment satisfies by putting both
 * behind one nginx gateway — the tenant app at /, this API at /api (see
 * DEPLOY.md). If the frontend ever ends up on a different domain than the API,
 * this needs to become SameSite=None (which then makes the CSRF double-submit
 * check load-bearing instead of defense-in-depth).
 *
 * <p>Secure defaults to true and should stay that way in local dev — Chrome and
 * Firefox both treat http://localhost as a secure context, so Secure cookies
 * work there without relaxing anything. Do not weaken this for "dev convenience".
 */
public final class CookieUtil {

    public static final String REFRESH_COOKIE = "galaxy_refresh_token";
    public static final String CSRF_COOKIE = "galaxy_csrf_token";
    static final String REFRESH_COOKIE_PATH = "/api/auth";

    /**
     * Whether the auth cookies carry the Secure attribute. Set once at startup
     * from {@code galaxy.cookie.secure} by {@link CookieConfig}.
     *
     * <p>This is a flag rather than a constant for exactly one deployment shape:
     * serving over plain http:// on a bare IP, where there is no domain and so
     * no certificate. A browser silently discards every Secure cookie on such an
     * origin — no console error, no failed request, the refresh flow just never
     * works. Turning it off there is the difference between a working login and
     * an unexplainable one.
     *
     * <p>It costs real security: the refresh token and CSRF token then travel in
     * cleartext and are readable by anything on the network path. Any deployment
     * that has a domain and a certificate should leave this at true.
     */
    private static volatile boolean secure = true;

    private CookieUtil() {
    }

    /** Package-private on purpose — {@link CookieConfig} is the only caller. */
    static void setSecure(boolean value) {
        secure = value;
    }

    public static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Readable by JS on purpose — the frontend echoes this back as a header (double-submit). */
    public static ResponseCookie csrfCookie(String value, Duration maxAge) {
        return ResponseCookie.from(CSRF_COOKIE, value)
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie clearedCsrfCookie() {
        return ResponseCookie.from(CSRF_COOKIE, "")
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
