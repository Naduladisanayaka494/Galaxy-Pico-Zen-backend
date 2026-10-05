package com.knox.galaxy.service;

import com.knox.galaxy.dto.GalaxyPlanResponse;
import com.knox.galaxy.dto.SubscriptionPaymentResponse;
import com.knox.galaxy.dto.SubscriptionResponse;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.*;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The tenant's view of its own Galaxy subscription (the My Subscription page).
 *
 * <p>This reads the platform side — {@code knox.subscriptions},
 * {@code knox.galaxy_plans} and {@code knox.subscription_periods} — filtered to
 * the tenant bound on the request, and never exposes another tenant's billing.
 * It is entirely read-only: plan changes are KNOX staff's job, through the
 * client manager.
 *
 * <p>Distinct from {@link FinanceService}, which is the tenant's <em>own</em>
 * revenue and expense books. The two are unrelated ledgers.
 */
@Service
public class SubscriptionService {

    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPeriodRepository subscriptionPeriodRepository;
    @Autowired private GalaxyPlanRepository galaxyPlanRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    @Transactional(readOnly = true)
    public SubscriptionResponse get() {
        Subscription subscription = findSubscription();
        GalaxyPlan plan = galaxyPlanRepository.findById(subscription.getPlan()).orElse(null);

        // currentPeriodStart / currentPeriodEnd are set by KNOX staff edits;
        // they are null for subscriptions created before those fields existed, or
        // when KNOX hasn't entered them yet. Derive a sensible renewal date so
        // the "Next renewal" card is never empty.
        LocalDate periodEnd = subscription.getCurrentPeriodEnd();
        LocalDate periodStart = subscription.getCurrentPeriodStart();

        if (periodEnd == null) {
            // Prefer the most recent KNOX billing period (subscription_periods) —
            // that IS the renewal cadence. Next renewal = the month after the
            // last billed period start.
            Long tenantId = TenantContext.requireTenantId();
            Optional<Long> clientIdOpt = tenantRepository.findById(tenantId).map(Tenant::getClientId);
            if (clientIdOpt.isPresent() && clientIdOpt.get() != null) {
                Optional<SubscriptionPeriod> latest =
                        subscriptionPeriodRepository.findTopByClientIdOrderByPeriodStartDesc(clientIdOpt.get());
                if (latest.isPresent()) {
                    // Monthly billing: next renewal is the 1st of the month after the latest billed period.
                    periodStart = latest.get().getPeriodStart();
                    periodEnd   = periodStart.plusMonths(1);
                }
            }

            if (periodEnd == null) {
                // No billing periods recorded yet — fall back to startedAt-based
                // rolling window (always on the 1st of a month).
                LocalDate base = subscription.getStartedAt().withDayOfMonth(1);
                LocalDate now  = LocalDate.now();
                // Walk forward in monthly steps until we find the period that
                // contains today, then the end is the following month.
                while (!base.plusMonths(1).isAfter(now)) {
                    base = base.plusMonths(1);
                }
                periodStart = base;
                periodEnd   = base.plusMonths(1);
            }
        }

        Integer daysUntilRenewal = (int) ChronoUnit.DAYS.between(LocalDate.now(), periodEnd);

        return new SubscriptionResponse(
                subscription.getPlan(),
                subscription.getStatus(),
                subscription.getStartedAt(),
                subscription.getTrialEndsAt(),
                periodStart,
                periodEnd,
                subscription.getOutstanding(),
                daysUntilRenewal,
                currentUsage(plan));
    }

    /** The full catalogue, with the tenant's own plan flagged. */
    @Transactional(readOnly = true)
    public List<GalaxyPlanResponse> plans() {
        BillingPlan current = findSubscription().getPlan();
        return galaxyPlanRepository.findAll().stream()
                .map(p -> new GalaxyPlanResponse(
                        p.getPlan(), p.getMonthlyPrice(), p.getMaxWarehouses(),
                        p.getMaxProducts(), p.getMaxOrdersMonth(), p.getMaxUsers(),
                        p.getPlan() == current))
                .collect(Collectors.toList());
    }

    /**
     * Billed periods, newest first.
     *
     * <p>{@code subscription_periods} is keyed by KNOX <em>client</em> id, not
     * tenant id, so this hops through {@code knox.tenants.client_id}. A tenant
     * created without a client record simply has no billing history yet.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPaymentResponse> payments() {
        Long tenantId = TenantContext.requireTenantId();
        Optional<Long> clientId = tenantRepository.findById(tenantId).map(Tenant::getClientId);
        if (clientId.isEmpty() || clientId.get() == null) {
            return Collections.emptyList();
        }
        List<SubscriptionPeriod> periods =
                subscriptionPeriodRepository.findByClientIdOrderByPeriodStartAsc(clientId.get());
        List<SubscriptionPaymentResponse> rows = periods.stream()
                .map(p -> new SubscriptionPaymentResponse(
                        p.getId(), p.getPeriodStart(), p.getAmount(), p.isPaid(), p.getPaidAt()))
                .collect(Collectors.toList());
        Collections.reverse(rows);
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The tenant's live subscription. Cancelled ones are skipped so a tenant
     * that resubscribed sees the active record.
     */
    private Subscription findSubscription() {
        Long tenantId = TenantContext.requireTenantId();
        return subscriptionRepository
                .findByTenantIdAndStatusNot(tenantId, SubscriptionStatus.cancelled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This tenant has no active subscription record"));
    }

    /** Counts run against the tenant schema bound to this request. */
    private SubscriptionResponse.Usage currentUsage(GalaxyPlan plan) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        long ordersThisMonth = orderRepository.countByOrderedAtBetween(
                monthStart.atStartOfDay(),
                monthStart.plusMonths(1).atStartOfDay().minusNanos(1));

        return new SubscriptionResponse.Usage(
                warehouseRepository.count(), plan == null ? null : plan.getMaxWarehouses(),
                productRepository.count(),   plan == null ? null : plan.getMaxProducts(),
                ordersThisMonth,             plan == null ? null : plan.getMaxOrdersMonth(),
                userRepository.count(),      plan == null ? null : plan.getMaxUsers());
    }
}
