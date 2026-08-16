package com.knox.galaxy.controller;

import com.knox.galaxy.dto.InventoryResponse;
import com.knox.galaxy.dto.StockAdjustmentRequest;
import com.knox.galaxy.dto.StockMovementResponse;
import com.knox.galaxy.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** Per-warehouse stock levels and the movement history behind them. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /** Both filters are optional and combine. */
    @GetMapping
    public ResponseEntity<List<InventoryResponse>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(inventoryService.list(warehouseId, productId));
    }

    /** Applies the movement and records it in one transaction. */
    @PostMapping("/adjust")
    public ResponseEntity<StockMovementResponse> adjust(
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.adjust(request, username));
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<StockMovementResponse>> movements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryService.movements(warehouseId, productId, page, size));
    }
}
