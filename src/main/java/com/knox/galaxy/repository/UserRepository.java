package com.knox.galaxy.repository;

import com.knox.galaxy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Reads {@code users} in the schema bound to the current tenant context.
 *
 * <p>The IgnoreCase variants exist to match the uq_users_username_lower /
 * uq_users_email_lower indexes. A case-sensitive check would pass for
 * "ISURU" when "isuru" exists and then blow up on insert as a constraint
 * violation instead of a clean validation error.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByOrderByFirstNameAscLastNameAsc();

    /**
     * Active members holding a given role. Guards the "don't delete or
     * deactivate the last owner" rule, which would otherwise lock the tenant
     * out of its own Users and Settings screens.
     */
    long countByRoleNameIgnoreCaseAndIsActiveTrue(String roleName);

    /** Members holding a role — shown on the roles list and guards role deletion. */
    long countByRoleId(Short roleId);
}
