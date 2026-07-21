package com.knox.galaxy.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Mints and reads the tenant-scoped JWT.
 *
 * <p>The token carries {@code tenant_id}, not a schema name. The schema is
 * looked up from knox.tenants on each request instead of being taken from the
 * token: SET search_path cannot be parameterised, so the string that reaches it
 * should only ever originate from a row we control.
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_USER_ID = "user_id";
    public static final String CLAIM_ROLE = "role";
    /** Marks a KNOX staff token: no tenant_id, binds no tenant schema. */
    public static final String CLAIM_PLATFORM = "platform";

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Platform (KNOX staff) tokens only — tenant tokens use accessTokenExpirationInMs.
     *  Platform auth has no refresh-token mechanism yet, so this stays long-lived. */
    @Value("${jwt.expiration}")
    private long jwtExpirationInMs;

    /** Short-lived on purpose: tenant sessions renew via POST /api/auth/refresh
     *  using the httpOnly refresh cookie (see RefreshTokenService), so the access
     *  token itself only needs to survive between refreshes, not a whole session. */
    @Value("${jwt.access-token-expiration:900000}")
    private long accessTokenExpirationInMs;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param subject the platform email; the tenant-local username is not unique
     *                across tenants and cannot identify a person on its own.
     */
    public String generateToken(String subject, Long tenantId, Long localUserId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationInMs);

        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_USER_ID, localUserId)
                .claim(CLAIM_ROLE, role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Mints a KNOX staff token. Carries no tenant_id: these tokens must never
     * cause a tenant schema to be bound, and must never satisfy a tenant-scoped
     * endpoint.
     */
    public String generatePlatformToken(String subject, Long platformUserId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_PLATFORM, true)
                .claim(CLAIM_USER_ID, platformUserId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isPlatformToken(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get(CLAIM_PLATFORM, Boolean.class));
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getSubjectFromJWT(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getTenantIdFromJWT(String token) {
        Number tenantId = parseClaims(token).get(CLAIM_TENANT_ID, Number.class);
        return tenantId == null ? null : tenantId.longValue();
    }

    public Long getUserIdFromJWT(String token) {
        Number userId = parseClaims(token).get(CLAIM_USER_ID, Number.class);
        return userId == null ? null : userId.longValue();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // Token is invalid, expired, malformed, or signature mismatch
        }
        return false;
    }
}
