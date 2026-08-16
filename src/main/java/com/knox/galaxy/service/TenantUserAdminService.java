package com.knox.galaxy.service;

import com.knox.galaxy.dto.TenantUserRequest;
import com.knox.galaxy.dto.TenantUserResponse;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.RoleRepository;
import com.knox.galaxy.repository.TenantUserRepository;
import com.knox.galaxy.repository.UserRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages the tenant's own members (the Users screen).
 *
 * <p>A member is two rows in two schemas, and both have to move together:
 * <ul>
 *   <li>{@code <tenant>.users} — profile, role, commission;</li>
 *   <li>{@code knox.tenant_users} — the login credential and the directory
 *       entry that lets login resolve a tenant before any schema is bound.</li>
 * </ul>
 *
 * <p>Unlike {@code TenantProvisioningService}, this can stay {@code @Transactional}:
 * the tenant schema is already bound by {@code JwtAuthenticationFilter} for the
 * whole request, and {@link TenantUser} is schema-qualified to {@code knox}, so
 * neither write needs a {@link TenantContext} switch mid-flight.
 */
@Service
public class TenantUserAdminService {

    /** The role that must always retain at least one active member. */
    private static final String OWNER_ROLE = "owner";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<TenantUserResponse> list() {
        return userRepository.findAllByOrderByFirstNameAscLastNameAsc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TenantUserResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TenantUserResponse create(TenantUserRequest req) {
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A password is required when adding a user");
        }
        requireUsernameAvailable(req.getUsername(), null);
        requireEmailAvailable(req.getEmail(), null);
        validateCommission(req);

        User user = new User();
        applyProfile(user, req);
        // Legacy column: login reads knox.tenant_users, but users.password_hash
        // is NOT NULL and the Spring Security contract still expects it set.
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        TenantUser directory = new TenantUser();
        directory.setTenantId(TenantContext.requireTenantId());
        directory.setEmail(req.getEmail().trim());
        directory.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        directory.setLocalUserId(user.getId());
        directory.setStatus(req.isActive() ? TenantUserStatus.active : TenantUserStatus.disabled);
        tenantUserRepository.save(directory);

        notificationService.raise(NotificationType.user_added,
                user.getFirstName() + " " + user.getLastName() + " was added",
                "Role: " + user.getRole().getName());

        return toResponse(user);
    }

    @Transactional
    public TenantUserResponse update(Long id, TenantUserRequest req) {
        User user = findOrThrow(id);
        requireUsernameAvailable(req.getUsername(), id);
        requireEmailAvailable(req.getEmail(), id);
        validateCommission(req);

        boolean losingOwner = isOwner(user) && !OWNER_ROLE.equalsIgnoreCase(resolveRole(req.getRoleId()).getName());
        if ((losingOwner || !req.isActive()) && isLastActiveOwner(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This is the last active owner — reassign the owner role to someone else first");
        }

        String previousEmail = user.getEmail();
        applyProfile(user, req);
        user.setUpdatedAt(LocalDateTime.now());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        user = userRepository.save(user);

        syncDirectory(user, previousEmail, req.getEmail().trim(), req.getPassword(), req.isActive());
        return toResponse(user);
    }

    @Transactional
    public TenantUserResponse setStatus(Long id, boolean active) {
        User user = findOrThrow(id);
        if (!active && isLastActiveOwner(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This is the last active owner — the tenant would be locked out");
        }
        user.setActive(active);
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        findDirectory(user).ifPresent(directory -> {
            directory.setStatus(active ? TenantUserStatus.active : TenantUserStatus.disabled);
            directory.setUpdatedAt(LocalDateTime.now());
            tenantUserRepository.save(directory);
        });
        return toResponse(user);
    }

    /**
     * Every FK into {@code users} is ON DELETE SET NULL or CASCADE, so history
     * survives with attribution dropped rather than blocking the delete. The
     * directory row goes first — leaving it behind would keep a login alive
     * pointing at a profile that no longer exists.
     */
    @Transactional
    public void delete(Long id) {
        User user = findOrThrow(id);
        if (isLastActiveOwner(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This is the last active owner and cannot be deleted");
        }
        findDirectory(user).ifPresent(tenantUserRepository::delete);
        userRepository.delete(user);
    }

    // ------------------------------------------------------------------ helpers

    private void applyProfile(User user, TenantUserRequest req) {
        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName().trim());
        user.setEmail(req.getEmail().trim());
        user.setUsername(req.getUsername().trim());
        user.setRole(resolveRole(req.getRoleId()));
        user.setPhone(req.getPhone());
        user.setAvatarUrl(req.getAvatarUrl());
        user.setActive(req.isActive());
        user.setCommissionEnabled(req.isCommissionEnabled());
        user.setCommissionMethod(req.isCommissionEnabled() ? req.getCommissionMethod() : null);
        user.setCommissionPercent(req.isCommissionEnabled() ? req.getCommissionPercent() : null);
        user.setCommissionUnitAmount(req.isCommissionEnabled() ? req.getCommissionUnitAmount() : null);
        user.setCommissionMinUnits(req.isCommissionEnabled() ? req.getCommissionMinUnits() : null);
    }

    /**
     * Keeps {@code knox.tenant_users} in step. The row is looked up by the
     * email it had <em>before</em> this edit, since that is still its key at
     * this point.
     */
    private void syncDirectory(User user, String previousEmail, String newEmail,
                               String newPassword, boolean active) {
        Optional<TenantUser> found = previousEmail == null
                ? Optional.empty()
                : tenantUserRepository.findByEmailIgnoreCase(previousEmail);

        TenantUser directory = found.orElseGet(() -> {
            // No directory row (legacy profile created before this endpoint
            // existed) — create one so the person can actually log in.
            TenantUser fresh = new TenantUser();
            fresh.setTenantId(TenantContext.requireTenantId());
            fresh.setLocalUserId(user.getId());
            return fresh;
        });

        directory.setEmail(newEmail);
        directory.setLocalUserId(user.getId());
        directory.setStatus(active ? TenantUserStatus.active : TenantUserStatus.disabled);
        directory.setUpdatedAt(LocalDateTime.now());
        if (newPassword != null && !newPassword.isBlank()) {
            directory.setPasswordHash(passwordEncoder.encode(newPassword));
        } else if (directory.getPasswordHash() == null) {
            // Brand-new row with no password supplied: fall back to the profile
            // hash so the NOT NULL column is satisfied.
            directory.setPasswordHash(user.getPasswordHash());
        }
        tenantUserRepository.save(directory);
    }

    private Optional<TenantUser> findDirectory(User user) {
        return user.getEmail() == null
                ? Optional.empty()
                : tenantUserRepository.findByEmailIgnoreCase(user.getEmail());
    }

    private Role resolveRole(Short roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Role " + roleId + " does not exist"));
    }

    private boolean isOwner(User user) {
        return user.getRole() != null && OWNER_ROLE.equalsIgnoreCase(user.getRole().getName());
    }

    /** True when this user is an active owner and no other active owner remains. */
    private boolean isLastActiveOwner(User user) {
        return isOwner(user)
                && user.isActive()
                && userRepository.countByRoleNameIgnoreCaseAndIsActiveTrue(OWNER_ROLE) <= 1;
    }

    /**
     * Mirrors the {@code commission_shape} check constraint so a bad
     * combination surfaces as a readable 400.
     */
    private void validateCommission(TenantUserRequest req) {
        if (!req.isCommissionEnabled()) {
            return;
        }
        if (req.getCommissionMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a commission method when commission is enabled");
        }
        if (req.getCommissionMethod() == CommissionMethod.product_percentage
                && req.getCommissionPercent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A commission percentage is required for the percentage method");
        }
        if (req.getCommissionMethod() == CommissionMethod.per_product_fixed
                && req.getCommissionUnitAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A per-unit amount is required for the fixed-per-product method");
        }
    }

    private void requireUsernameAvailable(String username, Long selfId) {
        userRepository.findByUsernameIgnoreCase(username.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Username '" + username.trim() + "' is already taken");
            }
        });
    }

    private void requireEmailAvailable(String email, Long selfId) {
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Email '" + email.trim() + "' is already in use");
            }
        });
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User " + id + " not found"));
    }

    private TenantUserResponse toResponse(User u) {
        return new TenantUserResponse(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getUsername(),
                u.getRole() == null ? null : u.getRole().getId(),
                u.getRole() == null ? null : u.getRole().getName(),
                u.getPhone(),
                u.getAvatarUrl(),
                u.isActive(),
                u.getLastLoginAt(),
                u.isCommissionEnabled(),
                u.getCommissionMethod(),
                u.getCommissionPercent(),
                u.getCommissionUnitAmount(),
                u.getCommissionMinUnits());
    }
}
