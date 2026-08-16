package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** The tenant's business settings as rendered by the General Settings page. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessSettingsResponse {
    private String businessName;
    private String category;
    private String address;
    private String phone;
    private String logoUrl;
    private String timeZone;
    private boolean timeFormat24h;
    private String currencyCode;
    private boolean themeDark;
    private Integer lowStockThreshold;
    private boolean emailAlerts;
    private boolean alertNewOrder;
    private boolean alertStatusUpdate;
    private boolean alertUserAdded;
    private boolean alertProductAdded;
    private LocalDateTime updatedAt;
}
