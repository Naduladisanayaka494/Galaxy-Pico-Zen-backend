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
 * A revenue or expense line in the tenant's own books (§10.5).
 *
 * <p>{@code source} separates the two kinds of row: {@code manual} entries are
 * typed in by staff and are the only ones the API lets you edit, while
 * {@code auto} rows are derived from orders and are owned by the system.
 */
@Entity
@Table(name = "finance_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class FinanceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always the first day of the month the entry belongs to. */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "finance_kind")
    @Type(type = "pgsql_enum")
    private FinanceKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "finance_source")
    @Type(type = "pgsql_enum")
    private FinanceSource source = FinanceSource.manual;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
