package com.knox.galaxy.controller;

import com.knox.galaxy.dto.DashboardSummaryResponse;
import com.knox.galaxy.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Headline numbers, a 12-month revenue trend and recent orders for Overview. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        return ResponseEntity.ok(reportService.dashboard());
    }
}
