package com.knox.galaxy.repository;

import com.knox.galaxy.model.FinanceEntry;
import com.knox.galaxy.model.FinanceKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Finance ledger reads. Date bounds are always supplied by the service (it
 * defaults them rather than passing nulls), so no query here binds a null —
 * see {@link OrderRepository} for why that matters with this driver.
 */
@Repository
public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, Long> {

    List<FinanceEntry> findByPeriodMonthBetweenOrderByPeriodMonthDescIdDesc(
            LocalDate from, LocalDate to);

    List<FinanceEntry> findByKindAndPeriodMonthBetweenOrderByPeriodMonthDescIdDesc(
            FinanceKind kind, LocalDate from, LocalDate to);

    /** Total for one kind over a window; zero when there are no rows. */
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinanceEntry f "
            + "WHERE f.kind = :kind AND f.periodMonth BETWEEN :from AND :to")
    BigDecimal sumByKindBetween(@Param("kind") FinanceKind kind,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /** Per-month totals for one kind, oldest first — drives the trend chart. */
    @Query("SELECT f.periodMonth, COALESCE(SUM(f.amount), 0) FROM FinanceEntry f "
            + "WHERE f.kind = :kind AND f.periodMonth BETWEEN :from AND :to "
            + "GROUP BY f.periodMonth ORDER BY f.periodMonth ASC")
    List<Object[]> monthlyTotals(@Param("kind") FinanceKind kind,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
