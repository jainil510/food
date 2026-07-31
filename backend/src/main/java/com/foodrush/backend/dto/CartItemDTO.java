package com.foodrush.backend.dto;

import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CartItemDTO(
        Long cartItemId,
        Long foodItemId,
        String name,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotal,
        boolean isAvailable
) {

    public static CartItemDTO from(CartItem cartItem) {
        FoodItem foodItem = cartItem.getFoodItem();
        BigDecimal subtotal = foodItem.getPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new CartItemDTO(
                cartItem.getId(),
                foodItem.getId(),
                foodItem.getName(),
                foodItem.getPrice(),
                cartItem.getQuantity(),
                subtotal,
                foodItem.isAvailable());
    }
}
