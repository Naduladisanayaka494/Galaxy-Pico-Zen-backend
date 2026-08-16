package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knox.galaxy.model.DiscountType;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create / edit a discount code.
 *
 * <p>The "percentage codes cannot exceed 100" rule is a DB check constraint;
 * the service checks it first so the UI gets a 400 with a readable message
 * instead of a constraint-violation 500.
 */
@Data
public class DiscountCodeRequest {

    @NotBlank(message = "Discount code is required")
    @Size(max = 50)
    private String code;

    @NotNull(message = "Discount kind is required")
    private DiscountType kind;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.00", message = "Discount value must be >= 0")
    private BigDecimal value;

    @JsonProperty("isActive")
    private boolean isActive = true;
}
