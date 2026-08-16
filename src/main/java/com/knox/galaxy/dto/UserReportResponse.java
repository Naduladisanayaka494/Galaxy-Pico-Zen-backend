package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Users tab — per-staff order outcomes over the window. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserReportResponse {
    private LocalDate from;
    private LocalDate to;
    private List<UserPerformance> users;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPerformance {
        private Long userId;
        private String name;
        private String role;
        private long orders;
        private long delivered;
        private long cancelled;
        private long returned;
        private BigDecimal revenue;
        private BigDecimal profit;
        private long unitsSold;

        /**
         * Earned on delivered orders, from the user's own commission settings.
         * Zero when commission is disabled or the minimum-units cap isn't met.
         */
        private BigDecimal commission;
    }
}
