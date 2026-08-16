package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Create / edit a city in the Province → District → Town dropdown source. */
@Data
public class CityRequest {

    @NotBlank(message = "Province is required")
    @Size(max = 100)
    private String province;

    @NotBlank(message = "District is required")
    @Size(max = 100)
    private String district;

    @NotBlank(message = "City name is required")
    @Size(max = 150)
    private String name;

    @JsonProperty("isActive")
    private boolean isActive = true;
}
