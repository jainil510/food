package com.foodrush.backend.dto;

import com.foodrush.backend.entity.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrderItemDTO(
        Long foodItemId,
        String foodItemName,
        Integer quantity,
        BigDecimal priceAtOrder,
        BigDecimal subtotal
) {

    public static OrderItemDTO from(OrderItem item) {
        BigDecimal subtotal = item.getPriceAtOrder()
                .multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new OrderItemDTO(
                item.getFoodItem().getId(),
                item.getFoodItem().getName(),
                item.getQuantity(),
                item.getPriceAtOrder(),
                subtotal);
    }
}
