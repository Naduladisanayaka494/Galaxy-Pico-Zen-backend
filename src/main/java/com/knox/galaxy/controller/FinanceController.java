package com.knox.galaxy.controller;

import com.knox.galaxy.dto.FinanceEntryRequest;
import com.knox.galaxy.dto.FinanceEntryResponse;
import com.knox.galaxy.dto.FinanceSummaryResponse;
import com.knox.galaxy.model.FinanceKind;
import com.knox.galaxy.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * The tenant's revenue / expense ledger (§10.5).
 *
 * <p>Only manual entries are writable — entries derived from orders return 409
 * on edit or delete.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    @Autowired
    private FinanceService financeService;

    /** Defaults to the last 12 months when no window is given. */
    @PreAuthorize("@perm.view('reports')")
    @GetMapping
    public ResponseEntity<List<FinanceEntryResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) FinanceKind kind) {
        return ResponseEntity.ok(financeService.list(from, to, kind));
    }

    @PreAuthorize("@perm.view('reports')")
    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(financeService.summary(from, to));
    }

    @PreAuthorize("@perm.full('reports')")
    @PostMapping
    public ResponseEntity<FinanceEntryResponse> create(
            @Valid @RequestBody FinanceEntryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.status(HttpStatus.CREATED).body(financeService.create(request, username));
    }

    @PreAuthorize("@perm.full('reports')")
    @PutMapping("/{id}")
    public ResponseEntity<FinanceEntryResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody FinanceEntryRequest request) {
        return ResponseEntity.ok(financeService.update(id, request));
    }

    @PreAuthorize("@perm.full('reports')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        financeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
