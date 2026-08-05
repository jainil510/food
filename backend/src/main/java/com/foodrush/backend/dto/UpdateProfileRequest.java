package com.foodrush.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        String name,

        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone must be a 10-digit number")
        String phone
) {
}
