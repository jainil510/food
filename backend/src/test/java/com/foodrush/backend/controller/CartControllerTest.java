package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.dto.CartItemDTO;
import com.foodrush.backend.dto.UpdateCartItemRequest;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.CartItemNotFoundException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CartService cartService;

    /*
     * Security filters are disabled, so nothing populates the SecurityContext for us - but
     * @AuthenticationPrincipal still reads from SecurityContextHolder. Seed it directly.
     */
    @BeforeEach
    void setUpPrincipal() {
        UserPrincipal principal = new UserPrincipal(User.builder()
                .id(USER_ID).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    private CartDTO sampleCart() {
        return new CartDTO(1L, "Spice Route", List.of(
                new CartItemDTO(1L, 10L, "Samosa", new BigDecimal("60.00"), 2,
                        new BigDecimal("120.00"), true)),
                new BigDecimal("120.00"));
    }

    @Test
    void getCart_returns200WithCartBody() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(sampleCart());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.restaurantName").value("Spice Route"))
                .andExpect(jsonPath("$.items[0].cartItemId").value(1))
                .andExpect(jsonPath("$.items[0].foodItemId").value(10))
                .andExpect(jsonPath("$.items[0].name").value("Samosa"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(120.00))
                .andExpect(jsonPath("$.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.total").value(120.00));
    }

    @Test
    void getCart_returns200WithEmptyCart_whenUserHasNone() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(CartDTO.empty());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                // isEmpty(), not doesNotExist(): Jackson writes the null field as an explicit
                // "restaurantId": null, so the path exists and only its value is empty.
                .andExpect(jsonPath("$.restaurantId").isEmpty())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0.00));
    }

    @Test
    void addItem_returns200AndPassesTheAuthenticatedUserId() throws Exception {
        when(cartService.addItemToCart(eq(USER_ID), any(AddToCartRequest.class))).thenReturn(sampleCart());

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(10L, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(120.00));

        verify(cartService).addItemToCart(eq(USER_ID), any(AddToCartRequest.class));
    }

    @Test
    void addItem_returns400_whenFoodItemIdMissing() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(null, 2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_returns400_whenQuantityIsZero() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(10L, 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_returns404_whenFoodItemDoesNotExist() throws Exception {
        when(cartService.addItemToCart(eq(USER_ID), any(AddToCartRequest.class)))
                .thenThrow(new FoodItemNotFoundException("Food item not found: 99"));

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(99L, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_returns409_whenItemIsFromADifferentRestaurant() throws Exception {
        when(cartService.addItemToCart(eq(USER_ID), any(AddToCartRequest.class)))
                .thenThrow(new CartConflictException(
                        "Cart can only contain items from one restaurant. Clear cart first?"));

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(20L, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cart can only contain items from one restaurant. Clear cart first?"));
    }

    @Test
    void updateItem_returns200() throws Exception {
        when(cartService.updateCartItem(USER_ID, 1L, 5)).thenReturn(sampleCart());

        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(5))))
                .andExpect(status().isOk());

        verify(cartService).updateCartItem(USER_ID, 1L, 5);
    }

    @Test
    void updateItem_acceptsQuantityZeroAsRemoval() throws Exception {
        when(cartService.updateCartItem(USER_ID, 1L, 0)).thenReturn(CartDTO.empty());

        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void updateItem_returns400_whenQuantityIsNegative() throws Exception {
        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(-1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateItem_returns404_whenLineIsNotInTheUsersCart() throws Exception {
        when(cartService.updateCartItem(USER_ID, 42L, 3))
                .thenThrow(new CartItemNotFoundException("Cart item not found: 42"));

        mockMvc.perform(put("/api/cart/items/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_returns200WithUpdatedCart() throws Exception {
        when(cartService.removeCartItem(USER_ID, 1L)).thenReturn(CartDTO.empty());

        mockMvc.perform(delete("/api/cart/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.00));

        verify(cartService).removeCartItem(USER_ID, 1L);
    }

    @Test
    void removeItem_returns404_whenLineIsNotInTheUsersCart() throws Exception {
        when(cartService.removeCartItem(USER_ID, 42L))
                .thenThrow(new CartItemNotFoundException("Cart item not found: 42"));

        mockMvc.perform(delete("/api/cart/items/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_returns200WithEmptyCart() throws Exception {
        when(cartService.clearCart(USER_ID)).thenReturn(CartDTO.empty());

        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0.00));

        verify(cartService).clearCart(USER_ID);
    }
}
