package com.knox.galaxy.controller;

import com.knox.galaxy.dto.*;
import com.knox.galaxy.model.OrderStatus;
import com.knox.galaxy.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Orders for the Place Order and Manage Orders screens.
 *
 * <p>Placing an order reserves stock; the status endpoint is what later turns
 * that reservation into a shipment or releases it. See {@link OrderService}
 * for why none of this touches {@code stock_movements}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /** Paged, newest first. Both filters are optional. */
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.list(status, search, page, size));
    }

    /** Full order including its line items. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(request, username));
    }

    /** A reason is required for cancelled / returned / refunded. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.ok(orderService.updateStatus(id, request, username));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderStatusHistoryResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.history(id));
    }
}
