package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {
    private Long id;
    private String name;

    /** See {@link CityResponse#isActive} for why the name is pinned. */
    @JsonProperty("isActive")
    private boolean isActive;
}
