package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * Full replace of the tenant's business settings (the single
 * {@code business_settings} row). Every settable field is present — the
 * General Settings page sends the whole form back on save.
 *
 * <p>{@code logoUrl} accepts either an existing URL or a base64 data URI; the
 * service uploads the latter to S3 the same way product images are handled.
 */
@Data
public class BusinessSettingsRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 255)
    private String businessName;

    /** Retail, Wholesale, … — free text, validated by the UI's option list. */
    @Size(max = 100)
    private String category;

    @Size(max = 500)
    private String address;

    @Size(max = 50)
    private String phone;

    private String logoUrl;

    @NotBlank(message = "Time zone is required")
    @Size(max = 100)
    private String timeZone;

    private boolean timeFormat24h = true;

    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be a 3-letter ISO code")
    private String currencyCode;

    private boolean themeDark = false;

    @NotNull(message = "Low-stock threshold is required")
    @Min(value = 0, message = "Low-stock threshold must be >= 0")
    private Integer lowStockThreshold;

    private boolean emailAlerts = false;
    private boolean alertNewOrder = true;
    private boolean alertStatusUpdate = true;
    private boolean alertUserAdded = true;
    private boolean alertProductAdded = true;
}
