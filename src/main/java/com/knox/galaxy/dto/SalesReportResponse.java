package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Orders tab. Revenue, cost and profit count delivered orders only — an order
 * that was cancelled or returned never became money.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportResponse {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;

    /** Profit as a percentage of revenue; null when there was no revenue. */
    private BigDecimal marginPercent;

    private long deliveredOrders;
    private long totalOrders;

    /** order_status value → count, for every status seen in the window. */
    private Map<String, Long> ordersByStatus;

    private List<MonthPoint> months;
    private List<LostOrder> lostOrders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthPoint {
        private LocalDate month;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private long orders;
        private long cancelled;
        private long returned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LostOrder {
        private String orderCode;
        private String status;
        private String customerName;
        private String reason;
        private LocalDateTime orderedAt;
    }
}
