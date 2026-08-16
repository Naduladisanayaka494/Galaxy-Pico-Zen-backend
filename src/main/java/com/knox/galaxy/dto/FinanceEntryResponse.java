package com.knox.galaxy.dto;

import com.knox.galaxy.model.FinanceKind;
import com.knox.galaxy.model.FinanceSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceEntryResponse {
    private Long id;
    private LocalDate periodMonth;
    private FinanceKind kind;

    /** 'manual' rows are editable; 'auto' rows are derived and read-only. */
    private FinanceSource source;

    private String description;
    private BigDecimal amount;
    private String createdBy;
    private LocalDateTime createdAt;

    /** Convenience for the UI so it doesn't re-derive the source rule. */
    private boolean editable;
}
