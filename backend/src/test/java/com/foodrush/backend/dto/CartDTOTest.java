package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CartDTOTest {

    private FoodItem foodItem(Long id, String name, String price, boolean available) {
        return FoodItem.builder()
                .id(id)
                .restaurant(Restaurant.builder().id(1L).name("Spice Route").build())
                .name(name)
                .price(new BigDecimal(price))
                .isAvailable(available)
                .build();
    }

    private Cart cartWith(CartItem... items) {
        return Cart.builder()
                .id(100L)
                .restaurant(Restaurant.builder().id(1L).name("Spice Route").build())
                .items(new ArrayList<>(List.of(items)))
                .build();
    }

    private CartItem cartItem(Long id, FoodItem foodItem, int quantity) {
        return CartItem.builder().id(id).foodItem(foodItem).quantity(quantity).build();
    }

    @Test
    void from_computesSubtotalPerLine() {
        Cart cart = cartWith(cartItem(1L, foodItem(10L, "Samosa", "60.00", true), 3));

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).cartItemId()).isEqualTo(1L);
        assertThat(dto.items().get(0).foodItemId()).isEqualTo(10L);
        assertThat(dto.items().get(0).name()).isEqualTo("Samosa");
        assertThat(dto.items().get(0).quantity()).isEqualTo(3);
        assertThat(dto.items().get(0).subtotal()).isEqualByComparingTo("180.00");
        assertThat(dto.items().get(0).isAvailable()).isTrue();
    }

    @Test
    void from_sumsTotalAcrossMultipleLinesAndQuantities() {
        Cart cart = cartWith(
                cartItem(1L, foodItem(10L, "Samosa", "60.00", true), 3),
                cartItem(2L, foodItem(11L, "Paneer Tikka", "240.50", true), 2));

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.total()).isEqualByComparingTo("661.00");
        assertThat(dto.restaurantId()).isEqualTo(1L);
        assertThat(dto.restaurantName()).isEqualTo("Spice Route");
    }

    @Test
    void from_flagsUnavailableLineAndExcludesItFromTotal() {
        Cart cart = cartWith(
                cartItem(1L, foodItem(10L, "Samosa", "60.00", true), 2),
                cartItem(2L, foodItem(11L, "Paneer Tikka", "220.00", false), 1));

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.items()).hasSize(2);
        assertThat(dto.items().get(1).isAvailable()).isFalse();
        // The unavailable line still reports its own subtotal...
        assertThat(dto.items().get(1).subtotal()).isEqualByComparingTo("220.00");
        // ...but does not contribute to the cart total.
        assertThat(dto.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void from_returnsNullRestaurantFields_whenCartHasNoRestaurant() {
        Cart cart = Cart.builder().id(100L).items(new ArrayList<>()).build();

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.restaurantId()).isNull();
        assertThat(dto.restaurantName()).isNull();
        assertThat(dto.items()).isEmpty();
        assertThat(dto.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void empty_returnsZeroTotalAndNoItems() {
        CartDTO dto = CartDTO.empty();

        assertThat(dto.restaurantId()).isNull();
        assertThat(dto.restaurantName()).isNull();
        assertThat(dto.items()).isEmpty();
        assertThat(dto.total()).isEqualByComparingTo("0.00");
    }
}
