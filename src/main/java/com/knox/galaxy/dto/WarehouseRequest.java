package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Create / edit a warehouse from the Warehouses page. */
@Data
public class WarehouseRequest {

    @NotBlank(message = "Warehouse name is required")
    @Size(max = 255)
    private String name;

    /** Short unique id, e.g. "COL". Stored upper-case. */
    @NotBlank(message = "Warehouse code is required")
    @Size(max = 20)
    private String code;

    @Size(max = 255)
    private String location;

    /** Distribution / Retail / Storage — free text, constrained by the UI. */
    @Size(max = 100)
    private String type;

    @Size(max = 150)
    private String manager;

    /** Total unit capacity; null means "not tracked", which hides the fill bar. */
    @Min(value = 0, message = "Capacity must be >= 0")
    private Integer capacity;

    @JsonProperty("isActive")
    private boolean isActive = true;
}
