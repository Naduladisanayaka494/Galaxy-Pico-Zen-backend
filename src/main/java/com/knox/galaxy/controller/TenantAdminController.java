package com.knox.galaxy.controller;

import com.knox.galaxy.model.Tenant;
import com.knox.galaxy.tenancy.ProvisionTenantCommand;
import com.knox.galaxy.tenancy.TenantProvisioningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * Platform control plane: creating tenants.
 *
 * <p>Authorized by SecurityConfig's {@code /api/platform/** -> PLATFORM_ADMIN}
 * rule, i.e. a KNOX staff token. It previously used a shared X-Platform-Key
 * header; that was replaced once real staff identities existed, because a
 * static secret cannot be attributed to a person, cannot be rotated per user,
 * and cannot be held safely by a browser app.
 */
@RestController
@RequestMapping("/api/platform/tenants")
public class TenantAdminController {

    private final TenantProvisioningService provisioningService;

    public TenantAdminController(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> provision(@Valid @RequestBody ProvisionTenantCommand command) {
        Tenant tenant = provisioningService.provision(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", tenant.getId(),
                "slug", tenant.getSlug(),
                "schemaName", tenant.getSchemaName(),
                "status", tenant.getStatus().name()
        ));
    }
}
