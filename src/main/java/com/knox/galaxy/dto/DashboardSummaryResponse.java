package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Overview screen: headline numbers, a revenue trend, and recent orders. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    /** Delivered-order revenue for the current calendar month. */
    private BigDecimal revenueThisMonth;

    /** Same figure for the previous month, so the UI can show a delta. */
    private BigDecimal revenueLastMonth;

    /** Percentage change month over month; null when last month was zero. */
    private BigDecimal revenueChangePercent;

    private long ordersThisMonth;
    private long totalCustomers;
    private long totalProducts;
    private long lowStockCount;
    private long outOfStockCount;

    /** Orders currently in a pre-delivery status — the work queue. */
    private long openOrders;

    /** Last 12 months of delivered revenue, oldest first. */
    private List<RevenuePoint> revenueTrend;

    /** The five most recent orders, newest first. */
    private List<OrderResponse> recentOrders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenuePoint {
        private LocalDate month;
        private BigDecimal revenue;
    }
}
