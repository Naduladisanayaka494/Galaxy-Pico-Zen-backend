package com.knox.galaxy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_settings", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    @Id
    private Short id = 1;

    @Column(name = "profit_margin_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal profitMarginPercent = new BigDecimal("65.00");

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
