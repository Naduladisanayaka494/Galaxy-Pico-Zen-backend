package com.knox.galaxy.dto;

import com.knox.galaxy.model.BillingPlan;
import com.knox.galaxy.model.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The tenant's own Galaxy subscription, as shown on the My Subscription page. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private BillingPlan plan;
    private SubscriptionStatus status;
    private LocalDate startedAt;
    private LocalDate trialEndsAt;
    private LocalDate currentPeriodStart;
    private LocalDate currentPeriodEnd;

    /** Unpaid balance carried on the account. */
    private BigDecimal outstanding;

    /** Days until currentPeriodEnd; negative once overdue, null if unset. */
    private Integer daysUntilRenewal;

    /** Live usage against the plan's caps. Null caps mean unlimited. */
    private Usage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private long warehouses;
        private Integer maxWarehouses;
        private long products;
        private Integer maxProducts;
        private long ordersThisMonth;
        private Integer maxOrdersMonth;
        private long users;
        private Integer maxUsers;
    }
}
