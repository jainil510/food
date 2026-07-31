package com.foodrush.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RestaurantRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        String description,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @Size(max = 50, message = "Cuisine type must be at most 50 characters")
        String cuisineType,

        @DecimalMin(value = "0.0", message = "Rating must be at least 0")
        @DecimalMax(value = "5.0", message = "Rating must be at most 5")
        @Digits(integer = 1, fraction = 1, message = "Rating must have at most 1 integer digit and 1 fraction digit")
        BigDecimal rating,

        @Pattern(regexp = "^$|^https?://.+", message = "Image URL must be a valid http(s) URL")
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl
) {
}
