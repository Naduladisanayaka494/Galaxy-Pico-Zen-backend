package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Finance tab — the two sources of money side by side.
 *
 * <p>Trading figures are derived from delivered orders; manual figures come
 * from {@code finance_entries}. They are reported separately as well as
 * combined so it stays obvious which numbers were typed in by hand.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReportResponse {
    private LocalDate from;
    private LocalDate to;

    private BigDecimal tradingRevenue;
    private BigDecimal tradingCost;
    private BigDecimal manualRevenue;
    private BigDecimal manualExpense;

    /** tradingRevenue + manualRevenue - tradingCost - manualExpense. */
    private BigDecimal net;

    private List<MonthPoint> months;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthPoint {
        private LocalDate month;
        private BigDecimal tradingRevenue;
        private BigDecimal tradingCost;
        private BigDecimal manualRevenue;
        private BigDecimal manualExpense;
        private BigDecimal net;
    }
}
