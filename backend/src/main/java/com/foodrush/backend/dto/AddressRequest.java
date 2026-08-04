package com.foodrush.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 50, message = "Label must be at most 50 characters")
        String label,

        @NotBlank(message = "Full address is required")
        @Size(max = 500, message = "Full address must be at most 500 characters")
        String fullAddress,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^\\d{6}$", message = "Pincode must be exactly 6 digits")
        String pincode
) {
}
