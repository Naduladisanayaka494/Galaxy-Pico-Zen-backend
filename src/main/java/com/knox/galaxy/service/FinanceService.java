package com.knox.galaxy.service;

import com.knox.galaxy.dto.FinanceEntryRequest;
import com.knox.galaxy.dto.FinanceEntryResponse;
import com.knox.galaxy.dto.FinanceSummaryResponse;
import com.knox.galaxy.model.FinanceEntry;
import com.knox.galaxy.model.FinanceKind;
import com.knox.galaxy.model.FinanceSource;
import com.knox.galaxy.model.User;
import com.knox.galaxy.repository.FinanceEntryRepository;
import com.knox.galaxy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The tenant's revenue / expense ledger (§10.5).
 *
 * <p>Only {@code manual} rows are writable. {@code auto} rows are derived from
 * order activity and are rejected for edit or delete — letting someone hand-edit
 * a derived figure would put the books out of step with the orders behind them.
 */
@Service
public class FinanceService {

    /** How far back an unbounded query reaches. */
    private static final int DEFAULT_WINDOW_MONTHS = 12;

    @Autowired
    private FinanceEntryRepository financeEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FinanceEntryResponse> list(LocalDate from, LocalDate to, FinanceKind kind) {
        LocalDate start = firstOfMonth(from != null ? from : LocalDate.now().minusMonths(DEFAULT_WINDOW_MONTHS));
        LocalDate end = firstOfMonth(to != null ? to : LocalDate.now());

        List<FinanceEntry> rows = kind == null
                ? financeEntryRepository.findByPeriodMonthBetweenOrderByPeriodMonthDescIdDesc(start, end)
                : financeEntryRepository.findByKindAndPeriodMonthBetweenOrderByPeriodMonthDescIdDesc(kind, start, end);
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate start = firstOfMonth(from != null ? from : LocalDate.now().minusMonths(DEFAULT_WINDOW_MONTHS));
        LocalDate end = firstOfMonth(to != null ? to : LocalDate.now());

        BigDecimal revenue = financeEntryRepository.sumByKindBetween(FinanceKind.revenue, start, end);
        BigDecimal expense = financeEntryRepository.sumByKindBetween(FinanceKind.expense, start, end);

        // Merge the two per-kind breakdowns into one row per month.
        Map<LocalDate, BigDecimal> revenueByMonth = toMap(
                financeEntryRepository.monthlyTotals(FinanceKind.revenue, start, end));
        Map<LocalDate, BigDecimal> expenseByMonth = toMap(
                financeEntryRepository.monthlyTotals(FinanceKind.expense, start, end));

        Set<LocalDate> allMonths = new TreeSet<>();
        allMonths.addAll(revenueByMonth.keySet());
        allMonths.addAll(expenseByMonth.keySet());

        List<FinanceSummaryResponse.MonthlyTotal> months = allMonths.stream()
                .map(month -> {
                    BigDecimal r = revenueByMonth.getOrDefault(month, BigDecimal.ZERO);
                    BigDecimal e = expenseByMonth.getOrDefault(month, BigDecimal.ZERO);
                    return new FinanceSummaryResponse.MonthlyTotal(month, r, e, r.subtract(e));
                })
                .collect(Collectors.toList());

        return new FinanceSummaryResponse(start, end, revenue, expense, revenue.subtract(expense), months);
    }

    @Transactional
    public FinanceEntryResponse create(FinanceEntryRequest req, String actingUsername) {
        FinanceEntry entry = new FinanceEntry();
        apply(entry, req);
        entry.setSource(FinanceSource.manual);
        if (actingUsername != null) {
            userRepository.findByUsernameIgnoreCase(actingUsername).ifPresent(entry::setCreatedBy);
        }
        return toResponse(financeEntryRepository.save(entry));
    }

    @Transactional
    public FinanceEntryResponse update(Long id, FinanceEntryRequest req) {
        FinanceEntry entry = findOrThrow(id);
        requireManual(entry);
        apply(entry, req);
        return toResponse(financeEntryRepository.save(entry));
    }

    @Transactional
    public void delete(Long id) {
        FinanceEntry entry = findOrThrow(id);
        requireManual(entry);
        financeEntryRepository.delete(entry);
    }

    // ---------------------------------------------------------------- helpers

    private void apply(FinanceEntry entry, FinanceEntryRequest req) {
        entry.setPeriodMonth(firstOfMonth(req.getPeriodMonth()));
        entry.setKind(req.getKind());
        entry.setDescription(req.getDescription().trim());
        entry.setAmount(req.getAmount());
    }

    private void requireManual(FinanceEntry entry) {
        if (entry.getSource() != FinanceSource.manual) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This entry is generated from order activity and can't be edited by hand");
        }
    }

    /** period_month is defined as the first day of the month. */
    private LocalDate firstOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    private Map<LocalDate, BigDecimal> toMap(List<Object[]> rows) {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Object[] row : rows) {
            totals.put((LocalDate) row[0], (BigDecimal) row[1]);
        }
        return totals;
    }

    private FinanceEntry findOrThrow(Long id) {
        return financeEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Finance entry " + id + " not found"));
    }

    private FinanceEntryResponse toResponse(FinanceEntry f) {
        User actor = f.getCreatedBy();
        return new FinanceEntryResponse(
                f.getId(), f.getPeriodMonth(), f.getKind(), f.getSource(),
                f.getDescription(), f.getAmount(),
                actor == null ? null : actor.getFirstName() + " " + actor.getLastName(),
                f.getCreatedAt(),
                f.getSource() == FinanceSource.manual);
    }
}
