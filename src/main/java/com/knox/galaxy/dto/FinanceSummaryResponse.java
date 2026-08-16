package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Revenue / expense totals over a window, plus the per-month trend. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSummaryResponse {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpense;

    /** totalRevenue - totalExpense; may be negative. */
    private BigDecimal net;

    private List<MonthlyTotal> months;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTotal {
        private LocalDate month;
        private BigDecimal revenue;
        private BigDecimal expense;
        private BigDecimal net;
    }
}
