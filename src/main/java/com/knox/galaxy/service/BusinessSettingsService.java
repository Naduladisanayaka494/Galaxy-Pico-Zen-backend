package com.knox.galaxy.service;

import com.knox.galaxy.dto.BusinessSettingsRequest;
import com.knox.galaxy.dto.BusinessSettingsResponse;
import com.knox.galaxy.model.BusinessSettings;
import com.knox.galaxy.repository.BusinessSettingsRepository;
import com.knox.galaxy.security.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Reads and updates the tenant's single {@code business_settings} row.
 *
 * <p>The row is seeded during tenant provisioning, so a missing row means the
 * tenant schema was not provisioned properly rather than a first-run state —
 * hence the 500 rather than lazily creating one.
 */
@Service
public class BusinessSettingsService {

    /** S3 key prefix for uploaded business logos. */
    private static final String LOGO_FOLDER = "logos";

    @Autowired
    private BusinessSettingsRepository businessSettingsRepository;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private PermissionService permissionService;

    @Transactional(readOnly = true)
    public BusinessSettingsResponse get() {
        return toResponse(findOrThrow());
    }

    /**
     * Applies only the parts of the form the caller is allowed to change.
     *
     * <p>The General Settings page is one row and one PUT, but the role matrix
     * splits it four ways: {@code settings_business} and
     * {@code settings_time_currency} are owner/admin in the seeded matrix,
     * while {@code settings_theme} and {@code settings_notifications} are
     * granted to every role. A single gate on the endpoint would therefore
     * either lock everyone out of their own theme or hand everyone the
     * business name, so the filtering happens per field here instead — fields
     * the caller cannot change keep the value already on the row, and the
     * response shows them what was actually saved.
     */
    @Transactional
    public BusinessSettingsResponse update(BusinessSettingsRequest req) {
        BusinessSettings settings = findOrThrow();

        if (permissionService.full("settings_business")) {
            settings.setBusinessName(req.getBusinessName());
            settings.setCategory(req.getCategory());
            settings.setAddress(req.getAddress());
            settings.setPhone(req.getPhone());
            // Accepts an existing URL unchanged, or uploads a fresh base64 data URI.
            settings.setLogoUrl(s3Service.uploadIfBase64(req.getLogoUrl(), LOGO_FOLDER));
            settings.setLowStockThreshold(req.getLowStockThreshold());
        }

        if (permissionService.full("settings_time_currency")) {
            settings.setTimeZone(req.getTimeZone());
            settings.setTimeFormat24h(req.isTimeFormat24h());
            settings.setCurrencyCode(req.getCurrencyCode().toUpperCase());
        }

        if (permissionService.full("settings_theme")) {
            settings.setThemeDark(req.isThemeDark());
        }

        if (permissionService.full("settings_notifications")) {
            settings.setEmailAlerts(req.isEmailAlerts());
            settings.setAlertNewOrder(req.isAlertNewOrder());
            settings.setAlertStatusUpdate(req.isAlertStatusUpdate());
            settings.setAlertUserAdded(req.isAlertUserAdded());
            settings.setAlertProductAdded(req.isAlertProductAdded());
        }

        settings.setUpdatedAt(LocalDateTime.now());

        return toResponse(businessSettingsRepository.save(settings));
    }

    private BusinessSettings findOrThrow() {
        return businessSettingsRepository.findById(BusinessSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Business settings row is missing for this tenant"));
    }

    private BusinessSettingsResponse toResponse(BusinessSettings s) {
        return new BusinessSettingsResponse(
                s.getBusinessName(),
                s.getCategory(),
                s.getAddress(),
                s.getPhone(),
                s.getLogoUrl(),
                s.getTimeZone(),
                s.isTimeFormat24h(),
                s.getCurrencyCode(),
                s.isThemeDark(),
                s.getLowStockThreshold(),
                s.isEmailAlerts(),
                s.isAlertNewOrder(),
                s.isAlertStatusUpdate(),
                s.isAlertUserAdded(),
                s.isAlertProductAdded(),
                s.getUpdatedAt());
    }
}
