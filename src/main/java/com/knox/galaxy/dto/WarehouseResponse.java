package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A warehouse plus its live stock totals.
 *
 * <p>The Add / Edit Product form only reads id/name/code/location; the
 * Warehouses management page uses the rest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {
    private Long id;
    private String name;
    private String code;
    private String location;
    private String type;
    private String manager;
    private Integer capacity;

    /** See {@link CityResponse#isActive} for why the name is pinned. */
    @JsonProperty("isActive")
    private boolean isActive;

    /** Units currently stored here, summed across every product. */
    private int onHand;

    /** Distinct products stored here. */
    private long productCount;

    /**
     * onHand as a percentage of capacity, rounded. Null when capacity is unset
     * or zero — the page hides the fill bar rather than showing a fake 0%.
     */
    private Integer fillPercent;
}
