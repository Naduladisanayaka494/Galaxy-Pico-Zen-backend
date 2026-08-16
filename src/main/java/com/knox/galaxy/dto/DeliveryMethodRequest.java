package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.math.BigDecimal;

/** Create / edit a delivery method and its default charge. */
@Data
public class DeliveryMethodRequest {

    @NotBlank(message = "Delivery method name is required")
    @Size(max = 150)
    private String name;

    @NotNull(message = "Charge is required")
    @DecimalMin(value = "0.00", message = "Charge must be >= 0")
    private BigDecimal charge;

    @JsonProperty("isActive")
    private boolean isActive = true;
}
