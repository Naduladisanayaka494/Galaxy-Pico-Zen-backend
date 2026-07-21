package com.knox.galaxy.service;

import com.knox.galaxy.model.RefreshToken;
import com.knox.galaxy.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Issues, rotates, and revokes tenant refresh tokens. The JWT access token
 * stays completely stateless — this service is only ever consulted from
 * POST /api/auth/refresh and POST /api/auth/logout, not on ordinary requests.
 *
 * <p>Rotation + reuse detection (standard OAuth2 refresh-token pattern): every
 * successful refresh revokes the token just used and issues a new one in the
 * same {@code family_id}. If a revoked token is ever presented again, that is
 * a stolen token being replayed — the whole family is killed immediately
 * rather than just the one token, forcing a full re-login on that session
 * instead of silently letting the thief keep going.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final long expirationDays;
    private final TransactionTemplate requiresNewTransaction;

    public RefreshTokenService(RefreshTokenRepository repository,
                               PlatformTransactionManager transactionManager,
                               @Value("${galaxy.refresh-token.expiration-days:30}") long expirationDays) {
        this.repository = repository;
        this.expirationDays = expirationDays;
        // revokeFamily() must survive even when the caller (rotate(), on reuse
        // detection) goes on to throw right afterward — a plain @Transactional
        // self-invocation would get silently rolled back along with the rest of
        // that method's transaction. REQUIRES_NEW forces it to commit on its own.
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public static class IssuedToken {
        public final String rawToken;
        public final LocalDateTime expiresAt;
        public final Long tenantUserId;

        IssuedToken(String rawToken, LocalDateTime expiresAt, Long tenantUserId) {
            this.rawToken = rawToken;
            this.expiresAt = expiresAt;
            this.tenantUserId = tenantUserId;
        }
    }

    /** Starts a brand-new rotation family — call this on login, never on refresh. */
    @Transactional
    public IssuedToken issue(Long tenantUserId) {
        return issueInFamily(tenantUserId, UUID.randomUUID());
    }

    /**
     * Validates a presented refresh token and rotates it.
     *
     * @throws ResponseStatusException 401 if the token is unknown, expired, or
     * already revoked (the reuse case, which also kills the whole family).
     */
    @Transactional
    public IssuedToken rotate(String rawToken) {
        String hash = hash(rawToken);
        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (existing.getRevokedAt() != null) {
            // Someone presented a token that was already rotated out — either a
            // replay of a stolen token, or a client retry racing a previous
            // refresh. Either way, treat the whole session as compromised.
            log.warn("Refresh token reuse detected for tenant_user {} family {}; revoking family",
                    existing.getTenantUserId(), existing.getFamilyId());
            revokeFamily(existing.getFamilyId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; please log in again");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        IssuedToken next = issueInFamily(existing.getTenantUserId(), existing.getFamilyId());

        existing.setRevokedAt(LocalDateTime.now());
        repository.save(existing);
        // The new row's id is only known after the save above returns, so wire
        // replacedBy as a second, cheap update rather than reordering — the
        // audit trail only needs to be eventually consistent, not atomic here.
        repository.findByTokenHash(hash(next.rawToken)).ifPresent(created -> {
            existing.setReplacedBy(created.getId());
            repository.save(existing);
        });

        return next;
    }

    /** Logout: kill every live token in this session's rotation family. */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> revokeFamily(t.getFamilyId()));
    }

    public void revokeFamily(UUID familyId) {
        requiresNewTransaction.executeWithoutResult(status -> {
            List<RefreshToken> live = repository.findByFamilyIdAndRevokedAtIsNull(familyId);
            LocalDateTime now = LocalDateTime.now();
            for (RefreshToken t : live) {
                t.setRevokedAt(now);
            }
            repository.saveAll(live);
        });
    }

    /** Deletes rows with no remaining audit value: naturally expired, or
     *  revoked (rotated out / logged out) more than 7 days ago. Runs daily —
     *  this table only ever grows otherwise, since every rotation leaves a
     *  revoked row behind. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeStale() {
        LocalDateTime now = LocalDateTime.now();
        repository.deleteStale(now, now.minusDays(7));
        log.info("Purged stale refresh tokens (expired, or revoked more than 7 days ago)");
    }

    private IssuedToken issueInFamily(Long tenantUserId, UUID familyId) {
        String raw = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expirationDays);

        RefreshToken token = new RefreshToken();
        token.setTenantUserId(tenantUserId);
        token.setTokenHash(hash(raw));
        token.setFamilyId(familyId);
        token.setExpiresAt(expiresAt);
        repository.save(token);

        return new IssuedToken(raw, expiresAt, tenantUserId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
