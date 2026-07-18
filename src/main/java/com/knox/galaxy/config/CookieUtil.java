package com.knox.galaxy.config;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Builds the two auth cookies. SameSite=Strict assumes the frontend and this
 * API are served same-origin in production (a reverse proxy or the same
 * domain) — the assumption this codebase currently makes since no production
 * domain topology exists yet. If the frontend ever ends up on a different
 * domain than the API, this needs to become SameSite=None (which then makes
 * the CSRF double-submit check load-bearing instead of defense-in-depth).
 *
 * <p>Secure=true is kept unconditionally, including in local dev — Chrome and
 * Firefox both treat http://localhost as a secure context, so Secure cookies
 * work there without relaxing anything. Do not weaken this for "dev convenience".
 */
public final class CookieUtil {

    public static final String REFRESH_COOKIE = "galaxy_refresh_token";
    public static final String CSRF_COOKIE = "galaxy_csrf_token";
    static final String REFRESH_COOKIE_PATH = "/api/auth";

    private CookieUtil() {
    }

    public static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Readable by JS on purpose — the frontend echoes this back as a header (double-submit). */
    public static ResponseCookie csrfCookie(String value, Duration maxAge) {
        return ResponseCookie.from(CSRF_COOKIE, value)
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie clearedCsrfCookie() {
        return ResponseCookie.from(CSRF_COOKIE, "")
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
