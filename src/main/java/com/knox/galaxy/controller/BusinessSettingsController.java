package com.knox.galaxy.controller;

import com.knox.galaxy.dto.BusinessSettingsRequest;
import com.knox.galaxy.dto.BusinessSettingsResponse;
import com.knox.galaxy.service.BusinessSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * The tenant's own business settings (General Settings page).
 *
 * <p>Not to be confused with {@code /api/platform/settings}, which is the
 * KNOX-staff-facing platform configuration.
 *
 * <p>Security: covered by SecurityConfig's blanket
 * {@code anyRequest().authenticated()} over /api/**.
 */
@RestController
@RequestMapping("/api/settings/business")
public class BusinessSettingsController {

    @Autowired
    private BusinessSettingsService businessSettingsService;

    @GetMapping
    public ResponseEntity<BusinessSettingsResponse> get() {
        return ResponseEntity.ok(businessSettingsService.get());
    }

    /**
     * Open to any authenticated user, and filtered per field by
     * {@link com.knox.galaxy.service.BusinessSettingsService#update} - the four
     * settings_* features in the role matrix do not divide along the same line
     * as this one row.
     */
    @PutMapping
    public ResponseEntity<BusinessSettingsResponse> update(
            @Valid @RequestBody BusinessSettingsRequest request) {
        return ResponseEntity.ok(businessSettingsService.update(request));
    }
}
