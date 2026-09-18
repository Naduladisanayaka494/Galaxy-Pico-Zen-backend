package com.knox.galaxy.controller;

import com.knox.galaxy.dto.CustomerRequest;
import com.knox.galaxy.dto.CustomerResponse;
import com.knox.galaxy.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** Customers, keyed by phone number. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @PreAuthorize("@perm.anyView('orders_view','place_order')")
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> list() {
        return ResponseEntity.ok(customerService.list());
    }

    /** Type-ahead for the Place Order customer field — matches name or phone. */
    @PreAuthorize("@perm.anyView('orders_view','place_order')")
    @GetMapping("/search")
    public ResponseEntity<Page<CustomerResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(customerService.search(query, page, size));
    }

    @PreAuthorize("@perm.anyView('orders_view','place_order')")
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.get(id));
    }

    @PreAuthorize("@perm.anyFull('place_order','orders_edit')")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @PreAuthorize("@perm.anyFull('place_order','orders_edit')")
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }
}
