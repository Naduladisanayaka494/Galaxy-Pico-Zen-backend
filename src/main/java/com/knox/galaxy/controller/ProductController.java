package com.knox.galaxy.controller;

import com.knox.galaxy.dto.ProductRequest;
import com.knox.galaxy.dto.ProductResponse;
import com.knox.galaxy.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the tenant product catalogue.
 *
 * Security: all routes sit under /api/** which is covered by
 * SecurityConfig → anyRequest().authenticated(). A valid tenant JWT is
 * required for every call; no extra annotations needed here.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    // -------------------------------------------------------------------------
    // GET /api/products
    // Query params: search (optional), activeOnly (optional), page (0-indexed, default 0), size (default 20)
    // -------------------------------------------------------------------------
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(productService.list(search, activeOnly, page, size));
    }

    // -------------------------------------------------------------------------
    // GET /api/products/{id}
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // -------------------------------------------------------------------------
    // POST /api/products
    // -------------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // PUT /api/products/{id}  (full update)
    // -------------------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/products/{id}  (soft-delete)
    // -------------------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // DELETE /api/products  (bulk soft-delete — ids in request body)
    // -------------------------------------------------------------------------
    @DeleteMapping
    public ResponseEntity<Void> bulkDelete(@RequestBody List<Long> ids) {
        productService.bulkDelete(ids);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // POST /api/products/bulk-delete  (alternative — avoids DELETE-with-body)
    // Body: [1, 2, 3]
    // -------------------------------------------------------------------------
    @PostMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDeletePost(@RequestBody List<Long> ids) {
        productService.bulkDelete(ids);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // PATCH /api/products/{id}/status
    // Body: { "active": true|false }
    // -------------------------------------------------------------------------
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> toggleStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {

        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(productService.toggleActive(id, active));
    }

}
