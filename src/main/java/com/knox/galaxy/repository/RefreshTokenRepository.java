package com.knox.galaxy.repository;

import com.knox.galaxy.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

    /** Purges rows nobody will ever need again: naturally expired, or revoked
     * (rotated out / logged out) long enough ago that no audit value remains. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.revokedAt < :revokedBefore")
    void deleteStale(@Param("now") LocalDateTime now, @Param("revokedBefore") LocalDateTime revokedBefore);
}
