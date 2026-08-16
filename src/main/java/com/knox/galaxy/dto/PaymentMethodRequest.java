package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Create / edit a payment method tag. */
@Data
public class PaymentMethodRequest {

    @NotBlank(message = "Payment method name is required")
    @Size(max = 150)
    private String name;

    @JsonProperty("isActive")
    private boolean isActive = true;
}
