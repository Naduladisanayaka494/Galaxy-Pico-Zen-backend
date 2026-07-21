package com.knox.galaxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One billing period for a client (§16.4).
 *
 * <p>{@code periodStart} is the first day of the month for monthly/per-order
 * plans, and 1 January for yearly plans. The UI's period keys ("2026-04",
 * "2026") map onto that; see PeriodKeys.
 *
 * <p>{@code amount} is null for unlimited/per-order clients until KNOX enters
 * the month's bill — "Not entered" in the UI, and distinct from zero.
 */
@Entity
@Table(name = "subscription_periods", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid = false;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
