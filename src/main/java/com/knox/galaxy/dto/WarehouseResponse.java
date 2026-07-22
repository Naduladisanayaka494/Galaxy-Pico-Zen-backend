package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only warehouse summary returned to the frontend so the
 * Add / Edit Product form can render per-warehouse quantity fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {
    private Long id;
    private String name;
    private String code;
    private String location;
}
