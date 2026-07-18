package com.knox.galaxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One setup-fee payment slot (§16.4): a single row for full payment, or four
 * rows for the installment option.
 */
@Entity
@Table(name = "setup_fee_installments", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetupFeeInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /** 1-4. Full-pay clients have exactly one row, numbered 1. */
    @Column(name = "installment_no", nullable = false)
    private Short installmentNo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid = false;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
