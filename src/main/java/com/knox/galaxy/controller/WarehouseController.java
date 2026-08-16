package com.knox.galaxy.controller;

import com.knox.galaxy.dto.WarehouseRequest;
import com.knox.galaxy.dto.WarehouseResponse;
import com.knox.galaxy.service.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Warehouses for the tenant UI.
 *
 * <p>{@code GET /api/warehouses} stays active-only so the Add / Edit Product
 * form keeps seeing just the warehouses it can assign stock to;
 * {@code /all} includes deactivated ones for the management page.
 */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    @Autowired
    private WarehouseService warehouseService;

    /** Active warehouses — used by Add/Edit Product form for per-warehouse qty fields. */
    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> listActive() {
        return ResponseEntity.ok(warehouseService.list(true));
    }

    /** All warehouses including inactive — used by Warehouse management page. */
    @GetMapping("/all")
    public ResponseEntity<List<WarehouseResponse>> listAll() {
        return ResponseEntity.ok(warehouseService.list(false));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.get(id));
    }

    @PostMapping
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouseService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WarehouseResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(warehouseService.update(id, request));
    }

    /** 409 while the warehouse still holds stock — deactivate instead. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
