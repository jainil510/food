package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.Restaurant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record CartDTO(
        Long restaurantId,
        String restaurantName,
        List<CartItemDTO> items,
        BigDecimal total
) {

    /**
     * Lines whose food item has since become unavailable are still returned, flagged, so the
     * user gets an explanation instead of a silently vanishing item - but they are left out
     * of the total, because they cannot be ordered.
     */
    public static CartDTO from(Cart cart) {
        List<CartItemDTO> items = cart.getItems().stream()
                .map(CartItemDTO::from)
                .toList();
        BigDecimal total = items.stream()
                .filter(CartItemDTO::isAvailable)
                .map(CartItemDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Restaurant restaurant = cart.getRestaurant();
        return new CartDTO(
                restaurant == null ? null : restaurant.getId(),
                restaurant == null ? null : restaurant.getName(),
                items,
                total);
    }

    /** Representation for a user who has no cart row yet. Deliberately does not create one. */
    public static CartDTO empty() {
        return new CartDTO(null, null, List.of(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
