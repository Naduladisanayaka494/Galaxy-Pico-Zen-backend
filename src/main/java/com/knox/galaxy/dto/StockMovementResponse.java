package com.knox.galaxy.dto;

import com.knox.galaxy.model.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of the stock movement history. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private StockMovementType type;
    private int quantity;
    private Long warehouseFromId;
    private String warehouseFromName;
    private Long warehouseToId;
    private String warehouseToName;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;

    /** Null when the user who made the change has since been deleted. */
    private String createdBy;

    private LocalDateTime createdAt;
}
