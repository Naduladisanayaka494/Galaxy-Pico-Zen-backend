package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One order line. Prices are the snapshots taken when the order was placed. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    /** Cost at order time — kept for the profit calc, not shown to customers. */
    private BigDecimal purchasePrice;

    private BigDecimal lineTotal;
}
