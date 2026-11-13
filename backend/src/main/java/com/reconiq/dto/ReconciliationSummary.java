package com.reconiq.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Result of one reconciliation run. */
public record ReconciliationSummary(
        LocalDateTime runAt,
        long totalRecords,
        long matched,
        long unmatched,
        long amountMismatch,
        long duplicate,
        long missing,
        long failed,
        long pending,
        BigDecimal matchedAmount,
        BigDecimal discrepancyAmount,
        long durationMs) {
}
