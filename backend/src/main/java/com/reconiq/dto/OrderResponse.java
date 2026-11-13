package com.reconiq.dto;

import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String orderId,
        String customerName,
        BigDecimal orderAmount,
        LocalDate orderDate,
        String gateway,
        OrderStatus status,
        LocalDateTime createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderId(),
                order.getCustomerName(),
                order.getOrderAmount(),
                order.getOrderDate(),
                order.getGateway(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
