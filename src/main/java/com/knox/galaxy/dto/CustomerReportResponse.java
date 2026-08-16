package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Customers tab. Spend counts delivered orders only. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerReportResponse {
    private LocalDate from;
    private LocalDate to;
    private long totalCustomers;

    /** Customers with more than one order in the window. */
    private long returningCustomers;

    private BigDecimal averageSpend;
    private List<CityTotal> byCity;
    private List<CustomerTotal> customers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CityTotal {
        private String city;
        private long orders;
        private BigDecimal revenue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerTotal {
        private Long customerId;
        private String name;
        private String phone;
        private String city;
        private long orders;
        private BigDecimal spend;
    }
}
