package com.knox.galaxy.controller;

import com.knox.galaxy.dto.ChangePasswordRequest;
import com.knox.galaxy.dto.TenantUserRequest;
import com.knox.galaxy.dto.TenantUserResponse;
import com.knox.galaxy.dto.UpdateProfileRequest;
import com.knox.galaxy.dto.UserResponseDto;
import com.knox.galaxy.model.User;
import com.knox.galaxy.service.TenantUserAdminService;
import com.knox.galaxy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * The signed-in person's own profile ({@code /me}), plus tenant member
 * management for the Users screen.
 *
 * <p>The literal {@code /me} routes are declared before {@code /{id}} so they
 * are never swallowed by the path variable.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TenantUserAdminService tenantUserAdminService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userDetails.getUsername()));
        return ResponseEntity.ok(toDto(user));
    }

    /** Name and phone only — see UpdateProfileRequest for why email isn't editable here. */
    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        User user = userService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(toDto(user));
    }

    /** Change your own password (Profile screen). 204 on success; 400 if the
     *  current password doesn't match. */
    @PutMapping("/me/password")
    public ResponseEntity<?> changeOwnPassword(@AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        userService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Tenant member management (Users screen)
    //
    // Writes are restricted to owner/admin, matching both the seeded
    // role_permissions matrix (the 'users' feature is granted to those two
    // only) and the pre-existing rule on POST /api/auth/register. Without it
    // any authenticated member — a delivery driver, say — could mint an owner
    // account or delete colleagues.
    // ---------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<TenantUserResponse>> list() {
        return ResponseEntity.ok(tenantUserAdminService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantUserAdminService.get(id));
    }

    /** Creates the tenant profile and the knox.tenant_users login together. */
    @PostMapping
    @PreAuthorize("hasAnyRole('owner','admin')")
    public ResponseEntity<TenantUserResponse> create(@Valid @RequestBody TenantUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantUserAdminService.create(request));
    }

    /** Omit {@code password} to leave the existing credential untouched. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('owner','admin')")
    public ResponseEntity<TenantUserResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody TenantUserRequest request) {
        return ResponseEntity.ok(tenantUserAdminService.update(id, request));
    }

    /** Body: { "active": true|false } — mirrors the products status endpoint. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('owner','admin')")
    public ResponseEntity<TenantUserResponse> setStatus(@PathVariable Long id,
                                                        @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(tenantUserAdminService.setStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('owner','admin')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tenantUserAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UserResponseDto toDto(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().getName());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setCommissionEnabled(user.isCommissionEnabled());
        dto.setCommissionMethod(user.getCommissionMethod());
        dto.setCommissionPercent(user.getCommissionPercent());
        dto.setCommissionUnitAmount(user.getCommissionUnitAmount());
        dto.setCommissionMinUnits(user.getCommissionMinUnits());
        return dto;
    }
}
