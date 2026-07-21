package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class PlatformSettingsRequest {

    @NotNull(message = "Profit margin percent is required")
    @DecimalMin(value = "0", message = "Profit margin percent cannot be negative")
    @DecimalMax(value = "100", message = "Profit margin percent cannot exceed 100")
    private BigDecimal profitMarginPercent;
}
