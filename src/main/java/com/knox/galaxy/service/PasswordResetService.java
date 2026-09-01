package com.knox.galaxy.service;

import com.knox.galaxy.model.PasswordResetToken;
import com.knox.galaxy.model.Tenant;
import com.knox.galaxy.model.TenantStatus;
import com.knox.galaxy.model.TenantUser;
import com.knox.galaxy.model.TenantUserStatus;
import com.knox.galaxy.model.User;
import com.knox.galaxy.repository.PasswordResetTokenRepository;
import com.knox.galaxy.repository.TenantRepository;
import com.knox.galaxy.repository.TenantUserRepository;
import com.knox.galaxy.repository.UserRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * "Forgot password" for tenant logins.
 *
 * <p>Nothing here is {@code @Transactional}, for the same reason as
 * {@link AuthService}: a single transaction would pin every query to whichever
 * schema was current when the Hibernate Session opened, so the knox lookup
 * (schema-qualified {@code knox.*} entities) and the tenant-schema write to
 * {@code users} must land in separate sessions with {@link TenantContext}
 * switched in between.
 *
 * <p>Token handling mirrors {@link RefreshTokenService}: only a SHA-256 hash of
 * the token is stored, never the raw value that goes in the email.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Deliberately identical wording for every failure path — "no such token",
     *  "already used" and "expired" must not be distinguishable to the caller. */
    private static final String INVALID_TOKEN = "This reset link is invalid or has expired. Request a new one.";

    private final PasswordResetTokenRepository tokenRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final long expirationMinutes;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                TenantUserRepository tenantUserRepository,
                                TenantRepository tenantRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                RefreshTokenService refreshTokenService,
                                @Value("${galaxy.password-reset.expiration-minutes:30}") long expirationMinutes) {
        this.tokenRepository = tokenRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Starts the flow: if {@code email} maps to an active account, mints a
     * single-use token and emails the reset link. Returns normally in every
     * other case too — an unknown or disabled address must be indistinguishable
     * from a real one, or this endpoint becomes an account-enumeration oracle.
     */
    public void requestReset(String email) {
        TenantContext.clear();

        TenantUser tenantUser = tenantUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (tenantUser == null || tenantUser.getStatus() != TenantUserStatus.active) {
            log.debug("Password reset requested for unknown/inactive email; responding as success anyway");
            return;
        }

        Tenant tenant = tenantRepository.findById(tenantUser.getTenantId()).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.active) {
            log.debug("Password reset requested for tenant not active; responding as success anyway");
            return;
        }

        // One live link at a time: a fresh request supersedes any still-valid
        // older one, so an intercepted earlier email can't be used later.
        invalidateOutstanding(tenantUser.getId());

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setTenantUserId(tenantUser.getId());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        tokenRepository.save(token);

        try {
            emailService.sendPasswordResetEmail(tenantUser.getEmail(), rawToken, expirationMinutes);
        } catch (MailException e) {
            // Don't surface this — the response must look the same as the
            // happy path. Logged so an operator can see mail is misconfigured.
            log.error("Failed to send password reset email to {}", tenantUser.getEmail(), e);
        }
    }

    /**
     * Completes the flow. Validates the token, sets the new password on the
     * platform credential (and mirrors it onto the tenant-local {@code users}
     * row), spends the token, and kills every existing session for that login.
     *
     * @throws ResponseStatusException 400 if the token is unknown, already
     * spent, or expired — all with the same message.
     */
    public void resetPassword(String rawToken, String newPassword) {
        TenantContext.clear();

        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN));

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN);
        }

        TenantUser tenantUser = tenantUserRepository.findById(token.getTenantUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN));

        // Spend the token first. If a later step fails, the safe outcome is a
        // dead link + unchanged password (request another), not a live link
        // that could set the password twice.
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        invalidateOutstanding(tenantUser.getId());

        String encoded = passwordEncoder.encode(newPassword);

        // Authoritative credential — this is what login checks.
        tenantUser.setPasswordHash(encoded);
        tenantUserRepository.save(tenantUser);

        // Any session that was already open may be the attacker's — force a
        // fresh login everywhere.
        refreshTokenService.revokeAllForUser(tenantUser.getId());

        // Keep the legacy tenant-local hash in step (unused by login, but
        // AuthService.register writes both and the Users screen surfaces it).
        mirrorToLocalUser(tenantUser, encoded);
    }

    private void mirrorToLocalUser(TenantUser tenantUser, String encoded) {
        if (tenantUser.getLocalUserId() == null) {
            return;
        }
        Tenant tenant = tenantRepository.findById(tenantUser.getTenantId()).orElse(null);
        if (tenant == null) {
            return;
        }
        TenantContext.setSchema(tenant.getSchemaName());
        try {
            userRepository.findById(tenantUser.getLocalUserId()).ifPresent(local -> {
                local.setPasswordHash(encoded);
                local.setUpdatedAt(LocalDateTime.now());
                userRepository.save(local);
            });
        } catch (RuntimeException e) {
            // The authoritative credential is already updated; a failure to
            // mirror it is a cosmetic inconsistency, not a broken login.
            log.warn("Password reset: failed to mirror new hash onto tenant-local user {}", tenantUser.getLocalUserId(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private void invalidateOutstanding(Long tenantUserId) {
        List<PasswordResetToken> outstanding = tokenRepository.findByTenantUserIdAndUsedAtIsNull(tenantUserId);
        if (outstanding.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PasswordResetToken t : outstanding) {
            t.setUsedAt(now);
        }
        tokenRepository.saveAll(outstanding);
    }

    /** Daily cleanup — this table only grows otherwise. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeStale() {
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.deleteStale(now, now.minusDays(7));
        log.info("Purged stale password reset tokens (expired, or used more than 7 days ago)");
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
