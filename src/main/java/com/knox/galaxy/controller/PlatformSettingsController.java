package com.knox.galaxy.controller;

import com.knox.galaxy.dto.PlatformSettingsRequest;
import com.knox.galaxy.model.PlatformSettings;
import com.knox.galaxy.service.PlatformSettingsService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Authorized by SecurityConfig's /api/platform/** -> PLATFORM_ADMIN rule.
 */
@RestController
@RequestMapping("/api/platform/settings")
public class PlatformSettingsController {

    private final PlatformSettingsService settingsService;

    public PlatformSettingsController(PlatformSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public PlatformSettings get() {
        return settingsService.get();
    }

    @PutMapping
    public PlatformSettings update(@Valid @RequestBody PlatformSettingsRequest request) {
        return settingsService.updateProfitMargin(request.getProfitMarginPercent());
    }
}
