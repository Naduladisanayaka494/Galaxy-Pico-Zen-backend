package com.knox.galaxy.controller;

import com.knox.galaxy.dto.*;
import com.knox.galaxy.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Read-only aggregates for the Reports tabs.
 *
 * <p>{@code from} and {@code to} are month-granular and both optional; the
 * service defaults to the last 12 months. Revenue counts delivered orders only.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/sales")
    public ResponseEntity<SalesReportResponse> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.sales(from, to));
    }

    /** Current holdings — a point-in-time view, so it takes no date window. */
    @GetMapping("/stock")
    public ResponseEntity<StockReportResponse> stock() {
        return ResponseEntity.ok(reportService.stock());
    }

    @GetMapping("/customers")
    public ResponseEntity<CustomerReportResponse> customers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.customers(from, to));
    }

    @GetMapping("/users")
    public ResponseEntity<UserReportResponse> users(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.users(from, to));
    }

    @GetMapping("/finance")
    public ResponseEntity<FinanceReportResponse> finance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.finance(from, to));
    }
}
