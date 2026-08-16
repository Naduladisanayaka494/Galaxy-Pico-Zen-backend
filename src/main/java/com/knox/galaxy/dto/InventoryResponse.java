package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One product's stock in one warehouse. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private Long productId;
    private String productCode;
    private String productName;
    private Long warehouseId;
    private String warehouseName;
    private int onHand;
    private int reserved;

    /** onHand minus reserved — what can actually be sold right now. */
    private int available;
}
