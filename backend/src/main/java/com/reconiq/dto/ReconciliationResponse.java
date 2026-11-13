package com.reconiq.dto;

import com.reconiq.entity.Reconciliation;
import com.reconiq.entity.ReconciliationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReconciliationResponse(
        Long id,
        String orderId,
        String transactionId,
        String gateway,
        BigDecimal orderAmount,
        BigDecimal transactionAmount,
        BigDecimal difference,
        ReconciliationStatus status,
        LocalDateTime reconciledAt) {

    public static ReconciliationResponse from(Reconciliation result) {
        return new ReconciliationResponse(
                result.getId(),
                result.getOrderId(),
                result.getTransactionId(),
                result.getGateway(),
                result.getOrderAmount(),
                result.getTransactionAmount(),
                result.getDifference(),
                result.getReconciliationStatus(),
                result.getReconciledAt());
    }
}
