package com.foodrush.backend.service;

import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private UserRepository userRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, foodItemRepository, userRepository);
    }

    static Restaurant restaurant(Long id, String name) {
        return Restaurant.builder().id(id).name(name).build();
    }

    static FoodItem foodItem(Long id, String name, String price, Restaurant restaurant, boolean available) {
        return FoodItem.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal(price))
                .restaurant(restaurant)
                .isAvailable(available)
                .build();
    }

    static CartItem cartItem(Long id, FoodItem foodItem, int quantity, Cart cart) {
        CartItem item = CartItem.builder().id(id).foodItem(foodItem).quantity(quantity).cart(cart).build();
        cart.getItems().add(item);
        return item;
    }

    static Cart cart(Long id, Restaurant restaurant) {
        return Cart.builder().id(id).restaurant(restaurant).items(new ArrayList<>()).build();
    }

    @Test
    void getCart_returnsEmptyCart_whenUserHasNoCartRow() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.restaurantId()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void getCart_doesNotCreateACartRow_whenUserHasNone() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        cartService.getCart(USER_ID);

        // A GET must never write. Creating a row here would litter the table for every user
        // who merely opens the cart page.
        verify(cartRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getCart_returnsItemsWithTotal() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, true), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.restaurantName()).isEqualTo("Spice Route");
        assertThat(result.items()).hasSize(2);
        assertThat(result.total()).isEqualByComparingTo("360.00");
    }

    @Test
    void getCart_excludesUnavailableLineFromTotalButStillReturnsIt() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, false), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(1).isAvailable()).isFalse();
        assertThat(result.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void getCart_returnsNullRestaurant_whenCartIsEmpty() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart(100L, null)));

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.restaurantId()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo("0.00");
    }
}
