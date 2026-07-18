package com.knox.galaxy.controller;

import com.knox.galaxy.config.CookieUtil;
import com.knox.galaxy.dto.LoginRequest;
import com.knox.galaxy.dto.LoginResponse;
import com.knox.galaxy.dto.RegisterRequest;
import com.knox.galaxy.model.User;
import com.knox.galaxy.service.AuthService;
import com.knox.galaxy.service.RefreshTokenService;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    // Same property RefreshTokenService uses for the DB row's expiry — the cookie's
    // Max-Age must track it exactly, so it's read from the same source, not duplicated.
    private final Duration refreshCookieMaxAge;

    public AuthController(AuthService authService,
                          RefreshTokenService refreshTokenService,
                          @Value("${galaxy.refresh-token.expiration-days:30}") long refreshExpirationDays) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookieMaxAge = Duration.ofDays(refreshExpirationDays);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthService.LoginResult result = authService.login(loginRequest.getEmail(), loginRequest.getPassword());
        return withAuthCookies(result);
    }

    /**
     * Exchanges a refresh token for a new access token, rotating the refresh
     * token in the process. Requires the CSRF header because the refresh
     * cookie alone is an ambient credential a cross-site request could ride
     * along on; the login endpoint doesn't need this since it proves a
     * password instead of relying on a cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request,
                                                 @CookieValue(name = CookieUtil.REFRESH_COOKIE, required = false) String refreshCookie,
                                                 @CookieValue(name = CookieUtil.CSRF_COOKIE, required = false) String csrfCookie) {
        requireCsrf(request, csrfCookie);
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token");
        }
        AuthService.LoginResult result = authService.refresh(refreshCookie);
        return withAuthCookies(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @CookieValue(name = CookieUtil.REFRESH_COOKIE, required = false) String refreshCookie,
                                       @CookieValue(name = CookieUtil.CSRF_COOKIE, required = false) String csrfCookie) {
        requireCsrf(request, csrfCookie);
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            refreshTokenService.revokeByRawToken(refreshCookie);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, CookieUtil.clearedRefreshCookie().toString())
                .header(HttpHeaders.SET_COOKIE, CookieUtil.clearedCsrfCookie().toString())
                .build();
    }

    /**
     * Adds a user to the caller's own tenant.
     *
     * <p>Authenticated and role-gated on purpose. The tenant is taken from the
     * caller's token and never from the request body: a body-supplied tenant on
     * an open endpoint would let anyone mint an 'owner' inside any tenant.
     * The first user of a brand-new tenant is created by provisioning, not here.
     */
    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('owner','admin')")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
        User savedUser = authService.register(TenantContext.requireTenantId(), registerRequest);
        return ResponseEntity.ok("User registered successfully with ID: " + savedUser.getId());
    }

    private ResponseEntity<LoginResponse> withAuthCookies(AuthService.LoginResult result) {
        String csrfToken = generateCsrfToken();
        ResponseCookie refreshCookie = CookieUtil.refreshCookie(result.refreshToken.rawToken, refreshCookieMaxAge);
        ResponseCookie csrfCookie = CookieUtil.csrfCookie(csrfToken, refreshCookieMaxAge);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .body(result.body);
    }

    private void requireCsrf(HttpServletRequest request, String csrfCookie) {
        String header = request.getHeader("X-XSRF-Token");
        if (csrfCookie == null || header == null || !csrfCookie.equals(header)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing or invalid CSRF token");
        }
    }

    private String generateCsrfToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
