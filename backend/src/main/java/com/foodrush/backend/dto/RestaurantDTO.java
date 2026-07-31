package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Restaurant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RestaurantDTO(
        Long id,
        String name,
        String description,
        String address,
        String cuisineType,
        BigDecimal rating,
        String imageUrl,
        LocalDateTime createdAt
) {

    public static RestaurantDTO from(Restaurant restaurant) {
        return new RestaurantDTO(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getAddress(),
                restaurant.getCuisineType(),
                restaurant.getRating(),
                restaurant.getImageUrl(),
                restaurant.getCreatedAt());
    }
}
