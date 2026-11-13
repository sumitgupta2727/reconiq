package com.reconiq.dto;

import com.reconiq.entity.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderRequest(
        @NotBlank(message = "Order ID is required")
        @Size(max = 30, message = "Order ID must be at most 30 characters")
        String orderId,

        @NotBlank(message = "Customer name is required")
        @Size(max = 100, message = "Customer name must be at most 100 characters")
        String customerName,

        @NotNull(message = "Order amount is required")
        @DecimalMin(value = "0.01", message = "Order amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Order amount must have at most 2 decimals")
        BigDecimal orderAmount,

        @NotNull(message = "Order date is required")
        LocalDate orderDate,

        @Size(max = 30, message = "Gateway must be at most 30 characters")
        String gateway,

        OrderStatus status) {
}
