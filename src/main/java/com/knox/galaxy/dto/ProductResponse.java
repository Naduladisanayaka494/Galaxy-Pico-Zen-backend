package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API response for a single product or a page of products.
 * totalStock is the sum of on_hand across all warehouses at query time.
 */
@Data
public class ProductResponse {

    private Long id;
    private String productCode;
    private String name;
    private String description;



    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private Integer lowStockThreshold;

    /**
     * Explicit name: Lombok generates {@code isActive()} for this field, which
     * Jackson publishes as {@code "active"} — while every frontend interface
     * reads {@code isActive}, so the flag arrived as undefined. Pinned to match
     * the rest of the API.
     */
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDate addedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Ordered list of images (position 1..n). Empty if none stored. */
    private List<ProductImageDto> images;

    /** Total on_hand quantity across all warehouses. */
    private int totalStock;
    private List<String> warehouses;
}
