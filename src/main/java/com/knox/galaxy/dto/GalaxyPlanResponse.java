package com.knox.galaxy.dto;

import com.knox.galaxy.model.BillingPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One plan from the catalogue, for the comparison cards.
 *
 * <p>Null limits mean unlimited; a null {@code monthlyPrice} means the plan is
 * quoted rather than listed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GalaxyPlanResponse {
    private BillingPlan plan;
    private BigDecimal monthlyPrice;
    private Integer maxWarehouses;
    private Integer maxProducts;
    private Integer maxOrdersMonth;
    private Integer maxUsers;

    /** True for the plan this tenant is currently on. */
    private boolean current;
}
