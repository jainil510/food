package com.foodrush.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        /*
         * 0 is allowed here but not on AddToCartRequest: updating to 0 is the documented way
         * to remove a line, while adding zero of something is meaningless.
         */
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be 0 or greater")
        Integer quantity
) {
}
