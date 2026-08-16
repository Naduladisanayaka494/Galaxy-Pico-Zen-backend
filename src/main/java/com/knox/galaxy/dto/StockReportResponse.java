package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Stock tab — current holdings valued at both cost and selling price. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReportResponse {
    private long totalProducts;

    /** On-hand units valued at purchase price — what the stock cost. */
    private BigDecimal stockPurchaseValue;

    /** On-hand units valued at selling price — what it would fetch. */
    private BigDecimal stockSellingValue;

    /** stockSellingValue - stockPurchaseValue. */
    private BigDecimal potentialProfit;

    private long lowStockCount;
    private long outOfStockCount;

    /** Every product, alphabetical. The UI filters this for its sub-tables. */
    private List<ProductStock> products;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductStock {
        private Long productId;
        private String productCode;
        private String productName;
        private int onHand;
        private BigDecimal purchasePrice;
        private BigDecimal sellingPrice;
        private int lowStockThreshold;
        private BigDecimal purchaseValue;
        private BigDecimal sellingValue;
        private BigDecimal potentialProfit;

        /** Delivered units all-time — zero means it has never sold. */
        private long unitsSold;

        private boolean lowStock;

        /** Holds stock but has never sold a unit. */
        private boolean deadStock;
    }
}
