package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knox.galaxy.model.DeliveryChargeKind;
import com.knox.galaxy.model.DeliveryRegionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMethodResponse {
    private Long id;
    private String name;

    /** LKR, or a percent of the items subtotal — see {@link #chargeKind}. */
    private BigDecimal charge;

    private DeliveryChargeKind chargeKind;

    /** {@code flat} means {@link #rates} is not consulted when pricing. */
    private DeliveryRegionType rateScope;

    /** Every rate on record, both scopes, so the settings grid can show them. */
    private List<DeliveryMethodRateResponse> rates = new ArrayList<>();

    /** See {@link CityResponse#isActive} for why the name is pinned. */
    @JsonProperty("isActive")
    private boolean isActive;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryMethodRateResponse {
        private Long id;
        private DeliveryRegionType regionType;
        private String regionName;
        private BigDecimal charge;
    }
}
