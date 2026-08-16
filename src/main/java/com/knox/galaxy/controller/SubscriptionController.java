package com.knox.galaxy.controller;

import com.knox.galaxy.dto.GalaxyPlanResponse;
import com.knox.galaxy.dto.SubscriptionPaymentResponse;
import com.knox.galaxy.dto.SubscriptionResponse;
import com.knox.galaxy.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The tenant's own Galaxy subscription (My Subscription page).
 *
 * <p>Read-only by design: changing a plan is KNOX staff's job through the client
 * manager under {@code /api/platform/clients}. Not to be confused with
 * {@code /api/finance}, which is the tenant's own books.
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    /** Current plan, renewal window, outstanding balance and live usage. */
    @GetMapping
    public ResponseEntity<SubscriptionResponse> get() {
        return ResponseEntity.ok(subscriptionService.get());
    }

    /** The plan catalogue, with this tenant's plan flagged. */
    @GetMapping("/plans")
    public ResponseEntity<List<GalaxyPlanResponse>> plans() {
        return ResponseEntity.ok(subscriptionService.plans());
    }

    /** Billed periods, newest first. Empty when the tenant has no client record. */
    @GetMapping("/payments")
    public ResponseEntity<List<SubscriptionPaymentResponse>> payments() {
        return ResponseEntity.ok(subscriptionService.payments());
    }
}
