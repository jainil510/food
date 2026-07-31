package com.foodrush.backend.dto;

public record AuthResponse(
        String token,
        Long userId,
        String name,
        String role
) {
}
