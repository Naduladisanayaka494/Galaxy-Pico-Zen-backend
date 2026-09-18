package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knox.galaxy.model.DeliveryChargeKind;
import com.knox.galaxy.model.DeliveryRegionType;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Create / edit a delivery method, its default charge, and its region rates. */
@Data
public class DeliveryMethodRequest {

    @NotBlank(message = "Delivery method name is required")
    @Size(max = 150)
    private String name;

    /**
     * The default charge — LKR, or a percent of the items subtotal when
     * {@link #chargeKind} is {@code percentage}. Also the fallback for any
     * region left blank in the rate grid.
     */
    @NotNull(message = "Charge is required")
    @DecimalMin(value = "0.00", message = "Charge must be >= 0")
    private BigDecimal charge;

    /** Null is accepted and means {@code fixed}, so old clients keep working. */
    private DeliveryChargeKind chargeKind;

    /** Null is accepted and means {@code flat} — one charge everywhere. */
    private DeliveryRegionType rateScope;

    /**
     * Per-region overrides. Rows for scopes other than {@link #rateScope} are
     * kept rather than dropped, so switching the toggle back in Settings finds
     * the earlier numbers still there.
     */
    @Valid
    private List<DeliveryMethodRateRequest> rates = new ArrayList<>();

    @Data
    public static class DeliveryMethodRateRequest {

        @NotNull(message = "Region type is required")
        private DeliveryRegionType regionType;

        @NotBlank(message = "Region name is required")
        @Size(max = 150)
        private String regionName;

        @NotNull(message = "Region charge is required")
        @DecimalMin(value = "0.00", message = "Region charge must be >= 0")
        private BigDecimal charge;
    }

    @JsonProperty("isActive")
    private boolean isActive = true;
}
