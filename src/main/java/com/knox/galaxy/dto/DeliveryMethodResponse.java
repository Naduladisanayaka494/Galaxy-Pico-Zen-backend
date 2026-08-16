package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMethodResponse {
    private Long id;
    private String name;
    private BigDecimal charge;

    /** See {@link CityResponse#isActive} for why the name is pinned. */
    @JsonProperty("isActive")
    private boolean isActive;
}
