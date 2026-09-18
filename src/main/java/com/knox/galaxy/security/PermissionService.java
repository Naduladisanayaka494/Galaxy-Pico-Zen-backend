package com.knox.galaxy.security;

import com.knox.galaxy.model.AccessLevel;
import com.knox.galaxy.model.RolePermission;
import com.knox.galaxy.model.User;
import com.knox.galaxy.repository.RolePermissionRepository;
import com.knox.galaxy.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The single place the role → feature matrix is enforced.
 *
 * <p>Registered under the bean name {@code perm} so controllers can read as
 * the permission they need:
 *
 * <pre>
 *   &#64;PreAuthorize("@perm.view('item_stock_view')")   // full | view | no_price
 *   &#64;PreAuthorize("@perm.full('item_stock_edit')")   // full only
 *   &#64;PreAuthorize("@perm.anyFull('add_product','warehouses')")
 * </pre>
 *
 * <p>Before this existed, {@code role_permissions} was data the Users screen
 * edited and nothing read: enforcement was eleven
 * {@code hasAnyRole('owner','admin')} annotations, which check the role's
 * <em>name</em> and so stopped working the moment a tenant renamed a role.
 * Those are all gone; every tenant endpoint now goes through the matrix, and a
 * renamed role keeps whatever access its matrix row grants.
 *
 * <h2>Two standing exemptions</h2>
 * <ul>
 *   <li><b>owner</b> — the seeded system role always gets full access, matrix
 *       or not. {@code RoleService} already refuses to edit its matrix, so
 *       this only guarantees a tenant can never lock itself out.</li>
 *   <li><b>ROLE_PLATFORM_ADMIN</b> — KNOX staff on {@code /api/platform/**},
 *       which SecurityConfig gates separately and which has no tenant role to
 *       look up.</li>
 * </ul>
 *
 * <h2>On not caching</h2>
 * Each check costs two small queries (the user, then its role's rows). A cache
 * would have to be keyed by tenant schema as well as role id — role ids repeat
 * across tenants — and a stale entry there is a cross-tenant access bug rather
 * than a slow page. At one check per request that trade is not worth taking;
 * revisit with a TenantContext-keyed cache if it ever shows up in a profile.
 */
@Component("perm")
public class PermissionService {

    /** The role whose access is never in question — see the class javadoc. */
    public static final String OWNER_ROLE = "owner";

    private static final String PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PermissionService(UserRepository userRepository,
                             RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    // ------------------------------------------------------------ predicates

    /** True for full, view or no_price — i.e. the feature is reachable at all. */
    @Transactional(readOnly = true)
    public boolean view(String feature) {
        return access(feature) != AccessLevel.none;
    }

    /** True only for full — the gate for anything that writes. */
    @Transactional(readOnly = true)
    public boolean full(String feature) {
        return access(feature) == AccessLevel.full;
    }

    /** Readable through any one of these features. */
    @Transactional(readOnly = true)
    public boolean anyView(String... features) {
        for (String feature : features) {
            if (view(feature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writable through any one of these features — for the handful of endpoints
     * that serve two screens, such as /api/inventory/adjust behind both Refill
     * (add_product) and Transfer (warehouses).
     */
    @Transactional(readOnly = true)
    public boolean anyFull(String... features) {
        for (String feature : features) {
            if (full(feature)) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------- lookups

    /**
     * The caller's access to one feature, {@code none} when unauthenticated,
     * when the user or role cannot be resolved, or when the matrix has no row
     * for it. Absent rows deny rather than allow: a feature added to the code
     * before it is added to a tenant's matrix must not be open to everyone in
     * the meantime.
     */
    @Transactional(readOnly = true)
    public AccessLevel access(String feature) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return AccessLevel.none;
        }
        if (hasAuthority(auth, PLATFORM_ADMIN)) {
            return AccessLevel.full;
        }

        Optional<User> user = currentUser(auth);
        if (!user.isPresent() || user.get().getRole() == null) {
            return AccessLevel.none;
        }
        if (OWNER_ROLE.equalsIgnoreCase(user.get().getRole().getName())) {
            return AccessLevel.full;
        }

        for (RolePermission permission : rolePermissionRepository.findByRole(user.get().getRole())) {
            if (permission.getFeature().equals(feature)) {
                return permission.getAccess();
            }
        }
        return AccessLevel.none;
    }

    /** True when the caller may see money on the Item Stock screen. */
    @Transactional(readOnly = true)
    public boolean seesPrices(String feature) {
        return access(feature) != AccessLevel.no_price && access(feature) != AccessLevel.none;
    }

    /**
     * The caller's own matrix, for the UI to gate on.
     *
     * <p>Deliberately not {@code /api/roles/{id}/permissions}: that one is
     * part of the Users screen and now needs {@code users} access, which most
     * roles do not have. Every user may read what they themselves can do.
     *
     * <p>owner comes back as full on every feature the matrix knows about, so
     * the UI does not have to special-case it the way {@link #access} does.
     */
    @Transactional(readOnly = true)
    public Map<String, AccessLevel> currentMatrix() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, AccessLevel> matrix = new LinkedHashMap<>();
        if (auth == null || !auth.isAuthenticated()) {
            return matrix;
        }

        Optional<User> user = currentUser(auth);
        if (!user.isPresent() || user.get().getRole() == null) {
            return matrix;
        }

        boolean owner = OWNER_ROLE.equalsIgnoreCase(user.get().getRole().getName());
        for (RolePermission permission : rolePermissionRepository.findByRole(user.get().getRole())) {
            matrix.put(permission.getFeature(), owner ? AccessLevel.full : permission.getAccess());
        }
        return matrix;
    }

    private Optional<User> currentUser(Authentication auth) {
        String username = auth.getName();
        if (username == null || username.isEmpty()) {
            return Optional.empty();
        }
        // Usernames are unique within a tenant, and the request is already
        // bound to one by TenantContext before any controller runs.
        return userRepository.findByUsernameIgnoreCase(username);
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
