package com.knox.galaxy.dto;

import com.knox.galaxy.model.StockMovementType;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Records a stock change and its audit row in one call.
 *
 * <p>Which warehouse fields are required depends on {@code type}:
 * {@code transfer} needs both {@code warehouseFromId} and {@code warehouseToId};
 * {@code initial_stock} and {@code refill} need only {@code warehouseToId}.
 * That mirrors the CHECK constraint on {@code stock_movements}.
 */
@Data
public class StockAdjustmentRequest {

    @NotNull(message = "Movement type is required")
    private StockMovementType type;

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than zero")
    private Integer quantity;

    /** Source warehouse — transfers only. */
    private Long warehouseFromId;

    /** Destination warehouse — required for every type. */
    private Long warehouseToId;

    @DecimalMin(value = "0.00", message = "Purchase price must be >= 0")
    private BigDecimal purchasePrice;

    @DecimalMin(value = "0.00", message = "Selling price must be >= 0")
    private BigDecimal sellingPrice;
}
