package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Billing state for a tenant — the single source of truth, replacing the
 * per-tenant {@code galaxy.subscription} table.
 */
@Entity
@Table(name = "subscriptions", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "billing_plan")
    @Type(type = "pgsql_enum")
    private BillingPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "subscription_status")
    @Type(type = "pgsql_enum")
    private SubscriptionStatus status = SubscriptionStatus.trialing;

    @Column(name = "started_at", nullable = false)
    private LocalDate startedAt = LocalDate.now();

    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "current_period_start")
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDate currentPeriodEnd;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal outstanding = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
