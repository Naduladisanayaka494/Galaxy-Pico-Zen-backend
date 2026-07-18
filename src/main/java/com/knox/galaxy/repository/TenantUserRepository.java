package com.knox.galaxy.repository;

import com.knox.galaxy.model.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    /**
     * Case-insensitive to match the uq_tenant_users_email_lower index. Anything
     * looser than the index would let two accounts differing only by case both
     * register and then collide at insert time.
     */
    Optional<TenantUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
