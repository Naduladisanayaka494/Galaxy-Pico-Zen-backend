package com.knox.galaxy.controller;

import com.knox.galaxy.dto.RolePermissionsRequest;
import com.knox.galaxy.dto.RoleResponse;
import com.knox.galaxy.model.AccessLevel;
import com.knox.galaxy.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/** Tenant roles and the role → feature access matrix behind the Users screen. */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleService.list());
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<Map<String, AccessLevel>> permissions(@PathVariable Short id) {
        return ResponseEntity.ok(roleService.permissions(id));
    }

    /**
     * Full replace — features omitted from the body are removed.
     *
     * <p>owner/admin only: rewriting the access matrix is how someone would
     * grant themselves everything, so it carries the same gate as user
     * management.
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('owner','admin')")
    public ResponseEntity<Map<String, AccessLevel>> replacePermissions(
            @PathVariable Short id, @Valid @RequestBody RolePermissionsRequest request) {
        return ResponseEntity.ok(roleService.replacePermissions(id, request));
    }
}
