package com.knox.galaxy.service;

import com.knox.galaxy.model.BillingPlan;
import com.knox.galaxy.model.GalaxyPlan;
import com.knox.galaxy.model.Subscription;
import com.knox.galaxy.model.SubscriptionStatus;
import com.knox.galaxy.repository.GalaxyPlanRepository;
import com.knox.galaxy.repository.SubscriptionRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single point of truth for subscription plan limit enforcement.
 *
 * <p>Call one of the {@code require*} methods at the start of any write
 * operation that is capped by the tenant's plan.  Each method reads the
 * current count, compares it to the plan's limit, and throws
 * {@code 403 FORBIDDEN} with a human-readable message when the cap would
 * be exceeded.  A {@code null} limit means "unlimited" – the check is
 * skipped in that case.
 *
 * <p>The subscription row lives in the {@code knox} schema (platform-side)
 * and is therefore accessible from any request regardless of which tenant
 * schema the thread is currently bound to.
 */
@Service
public class PlanLimitService {

    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private GalaxyPlanRepository galaxyPlanRepository;

    // ── warehouses ─────────────────────────────────────────────────────────

    /**
     * Throws 403 if the tenant is already at or above their warehouse cap.
     *
     * @param currentCount the number of warehouses that currently exist
     */
    public void requireWarehouseSlot(long currentCount) {
        GalaxyPlan plan = resolvedPlan();
        Integer max = plan.getMaxWarehouses();
        if (max != null && currentCount >= max) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your " + label(plan.getPlan()) + " plan allows a maximum of " + max
                            + " warehouse" + (max == 1 ? "" : "s") + ". "
                            + "Upgrade your plan to add more.");
        }
    }

    // ── products ───────────────────────────────────────────────────────────

    /**
     * Throws 403 if the tenant is already at or above their product cap.
     *
     * @param currentCount the total number of products in the catalogue
     */
    public void requireProductSlot(long currentCount) {
        GalaxyPlan plan = resolvedPlan();
        Integer max = plan.getMaxProducts();
        if (max != null && currentCount >= max) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your " + label(plan.getPlan()) + " plan allows a maximum of "
                            + max + " products. "
                            + "Upgrade your plan to add more.");
        }
    }

    // ── orders per month ───────────────────────────────────────────────────

    /**
     * Throws 403 if the tenant has reached or exceeded their monthly order cap.
     *
     * @param ordersThisMonth orders already placed in the current calendar month
     */
    public void requireOrderSlot(long ordersThisMonth) {
        GalaxyPlan plan = resolvedPlan();
        Integer max = plan.getMaxOrdersMonth();
        if (max != null && ordersThisMonth >= max) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your " + label(plan.getPlan()) + " plan allows a maximum of "
                            + max + " orders per month. "
                            + "Upgrade your plan to place more orders this month.");
        }
    }

    // ── users ──────────────────────────────────────────────────────────────

    /**
     * Throws 403 if the tenant is already at or above their user-account cap.
     *
     * @param currentCount active user accounts that already exist
     */
    public void requireUserSlot(long currentCount) {
        GalaxyPlan plan = resolvedPlan();
        Integer max = plan.getMaxUsers();
        if (max != null && currentCount >= max) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your " + label(plan.getPlan()) + " plan allows a maximum of " + max
                            + " user account" + (max == 1 ? "" : "s") + ". "
                            + "Upgrade your plan to add more users.");
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Resolves the active plan for the current tenant.
     *
     * <p>Reads from {@code knox.subscriptions} (platform schema), so this
     * works regardless of which tenant schema the thread is bound to.
     */
    private GalaxyPlan resolvedPlan() {
        Long tenantId = TenantContext.requireTenantId();
        Subscription subscription = subscriptionRepository
                .findByTenantIdAndStatusNot(tenantId, SubscriptionStatus.cancelled)
                .orElse(null);

        if (subscription == null) {
            // No active subscription – treat as Basic to be safe.
            return galaxyPlanRepository.findById(BillingPlan.basic).orElseThrow();
        }

        return galaxyPlanRepository.findById(subscription.getPlan())
                .orElseGet(() -> galaxyPlanRepository.findById(BillingPlan.basic).orElseThrow());
    }

    private static String label(BillingPlan plan) {
        return switch (plan) {
            case basic   -> "Basic";
            case nova    -> "Nova";
            case stellar -> "Stellar";
        };
    }
}

