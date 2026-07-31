package com.foodrush.backend.dto;

import com.foodrush.backend.entity.FoodItem;

import java.math.BigDecimal;

public record FoodItemDTO(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean isAvailable
) {

    public static FoodItemDTO from(FoodItem foodItem) {
        return new FoodItemDTO(
                foodItem.getId(),
                foodItem.getName(),
                foodItem.getDescription(),
                foodItem.getPrice(),
                foodItem.getImageUrl(),
                foodItem.isAvailable());
    }
}
