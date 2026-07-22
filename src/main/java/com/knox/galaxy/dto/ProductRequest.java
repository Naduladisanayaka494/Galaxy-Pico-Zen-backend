package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request body for creating and fully updating a product.
 * imageUrls may contain up to 5 entries; the first is treated as default.
 * warehouseQuantities maps warehouseId → on-hand quantity for initial stock.
 */
@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Product code is required")
    @Size(max = 100)
    private String productCode;

    @Size(max = 2000)
    private String description;



    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0.01", message = "Purchase price must be positive")
    private BigDecimal purchasePrice;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.01", message = "Selling price must be positive")
    private BigDecimal sellingPrice;

    /** Falls back to the business default (5) when null. */
    @Min(value = 0, message = "Low-stock threshold must be >= 0")
    private Integer lowStockThreshold;

    private boolean isActive = true;

    /** Up to 5 image URLs; position is inferred from list order (1-indexed).
     *  First entry is flagged as the default image. */
    @Size(max = 5, message = "Maximum 5 images allowed")
    private List<String> imageUrls;

    /**
     * Per-warehouse initial / updated stock quantities.
     * Key = warehouse ID, value = on-hand quantity (>= 0).
     * For a new product at least one value must be > 0.
     * For an update, only the warehouses listed here are touched;
     * warehouses omitted retain their existing stock.
     */
    private Map<Long, Integer> warehouseQuantities;
}

