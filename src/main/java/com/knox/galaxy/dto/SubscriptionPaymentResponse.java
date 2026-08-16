package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One billed subscription period — the payment history rows. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPaymentResponse {
    private Long id;
    private LocalDate periodStart;

    /** Null on per-order plans where the amount hasn't been entered yet. */
    private BigDecimal amount;

    private boolean paid;
    private LocalDateTime paidAt;
}
