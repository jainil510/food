package com.foodrush.backend.dto;

import com.foodrush.backend.entity.User;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {

    public static UserDTO from(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getCreatedAt());
    }
}
