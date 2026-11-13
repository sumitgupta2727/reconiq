package com.reconiq.dto;

import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String transactionId,
        String orderId,
        String customerName,
        String gateway,
        BigDecimal transactionAmount,
        LocalDateTime transactionDate,
        PaymentStatus paymentStatus,
        Long uploadBatchId,
        String uploadBatchFileName,
        LocalDateTime createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        Long batchId = transaction.getUploadBatch() == null ? null : transaction.getUploadBatch().getId();
        String batchName = transaction.getUploadBatch() == null ? null : transaction.getUploadBatch().getFileName();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionId(),
                transaction.getOrderId(),
                transaction.getCustomerName(),
                transaction.getGateway(),
                transaction.getTransactionAmount(),
                transaction.getTransactionDate(),
                transaction.getPaymentStatus(),
                batchId,
                batchName,
                transaction.getCreatedAt());
    }
}
