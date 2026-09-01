package com.knox.galaxy.repository;

import com.knox.galaxy.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Outstanding (unspent) links for one account — invalidated in bulk when a
     *  fresh link is requested or the password is changed by another route. */
    List<PasswordResetToken> findByTenantUserIdAndUsedAtIsNull(Long tenantUserId);

    /** Drops rows with no remaining value: expired, or spent long enough ago
     *  that the audit trail no longer needs them. Mirrors the refresh-token
     *  purge. */
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now OR t.usedAt < :usedBefore")
    void deleteStale(@Param("now") LocalDateTime now, @Param("usedBefore") LocalDateTime usedBefore);
}
