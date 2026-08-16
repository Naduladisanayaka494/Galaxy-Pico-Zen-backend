package com.knox.galaxy.controller;

import com.knox.galaxy.dto.DiscountCodeRequest;
import com.knox.galaxy.dto.DiscountCodeResponse;
import com.knox.galaxy.service.DiscountCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** Discount codes applied at order time. */
@RestController
@RequestMapping("/api/discount-codes")
public class DiscountCodeController {

    @Autowired
    private DiscountCodeService discountCodeService;

    @GetMapping
    public ResponseEntity<List<DiscountCodeResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(discountCodeService.list(activeOnly));
    }

    @PostMapping
    public ResponseEntity<DiscountCodeResponse> create(
            @Valid @RequestBody DiscountCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discountCodeService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscountCodeResponse> update(
            @PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        return ResponseEntity.ok(discountCodeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        discountCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
