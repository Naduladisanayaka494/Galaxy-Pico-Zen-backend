package com.knox.galaxy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessSettings {

    @Id
    private Short id = 1;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    private String category;
    private String address;
    private String phone;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "time_zone", nullable = false)
    private String timeZone = "Asia/Colombo";

    @Column(name = "time_format_24h", nullable = false)
    private boolean timeFormat24h = true;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "LKR";

    @Column(name = "theme_dark", nullable = false)
    private boolean themeDark = false;

    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 5;

    @Column(name = "email_alerts", nullable = false)
    private boolean emailAlerts = false;

    @Column(name = "alert_new_order", nullable = false)
    private boolean alertNewOrder = true;

    @Column(name = "alert_status_update", nullable = false)
    private boolean alertStatusUpdate = true;

    @Column(name = "alert_user_added", nullable = false)
    private boolean alertUserAdded = true;

    @Column(name = "alert_product_added", nullable = false)
    private boolean alertProductAdded = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
