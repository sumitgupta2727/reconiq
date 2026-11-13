package com.reconiq.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Aggregated numbers used by the dashboard, Reports page and PDF export. */
public record ReportSummaryResponse(
        LocalDateTime generatedAt,
        LocalDateTime lastReconciledAt,
        long totalOrders,
        long totalTransactions,
        long totalReconciliationRecords,
        long matchedRecords,
        long issueRecords,
        BigDecimal orderAmountTotal,
        BigDecimal transactionAmountTotal,
        BigDecimal netDifference,
        BigDecimal amountAtRisk,
        Map<String, Long> statusCounts,
        Map<String, BigDecimal> statusDifferences,
        List<GatewaySummary> gateways) {

    public record GatewaySummary(
            String gateway,
            long records,
            long matched,
            long issues,
            BigDecimal difference) {
    }
}
