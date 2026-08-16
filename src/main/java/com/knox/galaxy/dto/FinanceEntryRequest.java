package com.knox.galaxy.dto;

import com.knox.galaxy.model.FinanceKind;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create / edit a manual ledger line. {@code source} is not accepted — the API
 * only ever writes {@code manual} rows; {@code auto} rows are derived from
 * orders and belong to the system.
 */
@Data
public class FinanceEntryRequest {

    /** Any date within the month; the service normalises it to the 1st. */
    @NotNull(message = "A month is required")
    private LocalDate periodMonth;

    @NotNull(message = "Choose revenue or expense")
    private FinanceKind kind;

    @NotBlank(message = "A description is required")
    @Size(max = 500)
    private String description;

    @NotNull(message = "An amount is required")
    @DecimalMin(value = "0.00", message = "Amount must be >= 0")
    private BigDecimal amount;
}
