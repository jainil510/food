package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDTO(
        Long id,
        Long restaurantId,
        String restaurantName,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt
) {

    public static OrderDTO from(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getRestaurant().getId(),
                order.getRestaurant().getName(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
