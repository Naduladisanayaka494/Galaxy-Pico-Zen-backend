package com.knox.galaxy.service;

import com.knox.galaxy.model.PlatformSettings;
import com.knox.galaxy.repository.PlatformSettingsRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KNOX's own dashboard settings — currently just the assumed profit margin
 * (knox.platform_settings, single row). Platform-only: no tenant is bound.
 */
@Service
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;

    public PlatformSettingsService(PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlatformSettings get() {
        TenantContext.clear();
        return repository.findById((short) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "knox.platform_settings has no row; run knox_platform.sql"));
    }

    @Transactional
    public PlatformSettings updateProfitMargin(BigDecimal profitMarginPercent) {
        TenantContext.clear();
        PlatformSettings settings = get();
        settings.setProfitMarginPercent(profitMarginPercent);
        settings.setUpdatedAt(LocalDateTime.now());
        return repository.save(settings);
    }
}
