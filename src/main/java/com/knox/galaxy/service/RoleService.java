package com.knox.galaxy.service;

import com.knox.galaxy.dto.RolePermissionsRequest;
import com.knox.galaxy.dto.RoleResponse;
import com.knox.galaxy.model.AccessLevel;
import com.knox.galaxy.model.Role;
import com.knox.galaxy.model.RolePermission;
import com.knox.galaxy.repository.RolePermissionRepository;
import com.knox.galaxy.repository.RoleRepository;
import com.knox.galaxy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tenant roles and their feature-access matrix (§11.4).
 *
 * <p>Roles are per-tenant data, not an enum — a business can rename or add
 * them. The seeded 'owner' role is marked {@code is_system} and is protected
 * here, which is the enforcement the schema comment says was still missing.
 */
@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(r -> new RoleResponse(r.getId(), r.getName(), r.isSystem(),
                        userRepository.countByRoleId(r.getId())))
                .collect(Collectors.toList());
    }

    /** feature key → access level for one role. */
    @Transactional(readOnly = true)
    public Map<String, AccessLevel> permissions(Short roleId) {
        Role role = findOrThrow(roleId);
        Map<String, AccessLevel> matrix = new LinkedHashMap<>();
        for (RolePermission permission : rolePermissionRepository.findByRole(role)) {
            matrix.put(permission.getFeature(), permission.getAccess());
        }
        return matrix;
    }

    /**
     * Full replace of one role's matrix. The Users screen always posts the
     * whole column, so features missing from the payload are removed.
     */
    @Transactional
    public Map<String, AccessLevel> replacePermissions(Short roleId, RolePermissionsRequest req) {
        Role role = findOrThrow(roleId);
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + role.getName() + "' is a system role and its permissions are fixed");
        }

        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRole(role));
        // Flush the deletes before re-inserting: the two sets overlap on the
        // (role_id, feature) primary key, so interleaving them would collide.
        rolePermissionRepository.flush();

        for (Map.Entry<String, AccessLevel> entry : req.getPermissions().entrySet()) {
            RolePermission permission = new RolePermission();
            permission.setRole(role);
            permission.setFeature(entry.getKey());
            permission.setAccess(entry.getValue());
            rolePermissionRepository.save(permission);
        }
        return permissions(roleId);
    }

    private Role findOrThrow(Short roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Role " + roleId + " not found"));
    }
}
