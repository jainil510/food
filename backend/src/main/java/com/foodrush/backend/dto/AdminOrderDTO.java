package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDTO(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        Long restaurantId,
        String restaurantName,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        AddressDTO deliveryAddress,
        List<OrderItemDTO> items
) {

    public static AdminOrderDTO from(Order order) {
        return new AdminOrderDTO(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                order.getRestaurant().getId(),
                order.getRestaurant().getName(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                AddressDTO.from(order.getAddress()),
                order.getItems().stream().map(OrderItemDTO::from).toList());
    }
}
