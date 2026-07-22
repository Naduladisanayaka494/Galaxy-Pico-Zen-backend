package com.knox.galaxy.controller;

import com.knox.galaxy.dto.WarehouseResponse;
import com.knox.galaxy.model.Warehouse;
import com.knox.galaxy.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides read access to warehouses for the tenant UI.
 *
 * GET /api/warehouses          – all active warehouses (for Add/Edit Product form)
 * GET /api/warehouses/all      – all warehouses including inactive (for Warehouse management page)
 */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    @Autowired
    private WarehouseRepository warehouseRepository;

    /** Active warehouses — used by Add/Edit Product form for per-warehouse qty fields. */
    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> listActive() {
        List<WarehouseResponse> list = warehouseRepository
                .findAllByIsActiveOrderByNameAsc(true)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** All warehouses including inactive — used by Warehouse management page. */
    @GetMapping("/all")
    public ResponseEntity<List<WarehouseResponse>> listAll() {
        List<WarehouseResponse> list = warehouseRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    private WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(w.getId(), w.getName(), w.getCode(), w.getLocation());
    }
}
