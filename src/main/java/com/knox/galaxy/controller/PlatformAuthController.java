package com.knox.galaxy.controller;

import com.knox.galaxy.dto.PlatformLoginRequest;
import com.knox.galaxy.dto.PlatformLoginResponse;
import com.knox.galaxy.service.PlatformAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * KNOX staff login. Separate from /api/auth/login: that one resolves a tenant
 * and mints a tenant-scoped token, which is exactly what platform staff must
 * not have.
 */
@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    public PlatformAuthController(PlatformAuthService platformAuthService) {
        this.platformAuthService = platformAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<PlatformLoginResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        return ResponseEntity.ok(platformAuthService.login(request.getEmail(), request.getPassword()));
    }
}
