package com.reconiq.dto;

import com.reconiq.entity.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequest(
        @NotBlank(message = "Transaction ID is required")
        @Size(max = 30, message = "Transaction ID must be at most 30 characters")
        String transactionId,

        @Size(max = 30, message = "Order ID must be at most 30 characters")
        String orderId,

        @Size(max = 100, message = "Customer name must be at most 100 characters")
        String customerName,

        @NotBlank(message = "Gateway is required")
        @Size(max = 30, message = "Gateway must be at most 30 characters")
        String gateway,

        @NotNull(message = "Transaction amount is required")
        @DecimalMin(value = "0.00", message = "Transaction amount cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Transaction amount must have at most 2 decimals")
        BigDecimal transactionAmount,

        @NotNull(message = "Transaction date is required")
        LocalDateTime transactionDate,

        @NotNull(message = "Payment status is required")
        PaymentStatus paymentStatus) {
}
