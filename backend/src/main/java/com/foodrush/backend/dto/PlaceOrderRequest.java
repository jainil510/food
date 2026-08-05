package com.foodrush.backend.dto;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
        @NotNull(message = "Address ID is required")
        Long addressId
) {
}
