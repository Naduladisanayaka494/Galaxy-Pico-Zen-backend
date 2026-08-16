package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CityResponse {
    private Long id;
    private String province;
    private String district;
    private String name;

    /**
     * Explicit name: Lombok generates {@code isActive()} for this field, which
     * Jackson would otherwise publish as {@code "active"}. The frontend reads
     * {@code isActive}, so pin it.
     */
    @JsonProperty("isActive")
    private boolean isActive;
}
