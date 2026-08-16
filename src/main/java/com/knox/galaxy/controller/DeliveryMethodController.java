package com.knox.galaxy.controller;

import com.knox.galaxy.dto.DeliveryMethodRequest;
import com.knox.galaxy.dto.DeliveryMethodResponse;
import com.knox.galaxy.service.DeliveryMethodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** Delivery methods and their default charge. */
@RestController
@RequestMapping("/api/delivery-methods")
public class DeliveryMethodController {

    @Autowired
    private DeliveryMethodService deliveryMethodService;

    @GetMapping
    public ResponseEntity<List<DeliveryMethodResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(deliveryMethodService.list(activeOnly));
    }

    @PostMapping
    public ResponseEntity<DeliveryMethodResponse> create(
            @Valid @RequestBody DeliveryMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryMethodService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeliveryMethodResponse> update(
            @PathVariable Long id, @Valid @RequestBody DeliveryMethodRequest request) {
        return ResponseEntity.ok(deliveryMethodService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deliveryMethodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
