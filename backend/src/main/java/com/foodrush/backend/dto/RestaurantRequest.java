package com.foodrush.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record RestaurantRequest(
        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotBlank(message = "Address is required")
        String address,

        String cuisineType,

        @DecimalMin(value = "0.0", message = "Rating must be at least 0")
        @DecimalMax(value = "5.0", message = "Rating must be at most 5")
        BigDecimal rating,

        @Pattern(regexp = "^$|^https?://.+", message = "Image URL must be a valid http(s) URL")
        String imageUrl
) {
}
