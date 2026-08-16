package com.knox.galaxy.controller;

import com.knox.galaxy.dto.PaymentMethodRequest;
import com.knox.galaxy.dto.PaymentMethodResponse;
import com.knox.galaxy.service.PaymentMethodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** Payment method tags offered on the order form. */
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    @Autowired
    private PaymentMethodService paymentMethodService;

    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(paymentMethodService.list(activeOnly));
    }

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> create(
            @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMethodService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentMethodResponse> update(
            @PathVariable Long id, @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.ok(paymentMethodService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentMethodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
