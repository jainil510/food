package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailDTO(
        Long id,
        String restaurantName,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        AddressDTO deliveryAddress,
        List<OrderItemDTO> items
) {

    public static OrderDetailDTO from(Order order) {
        return new OrderDetailDTO(
                order.getId(),
                order.getRestaurant().getName(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                AddressDTO.from(order.getAddress()),
                order.getItems().stream().map(OrderItemDTO::from).toList());
    }
}
