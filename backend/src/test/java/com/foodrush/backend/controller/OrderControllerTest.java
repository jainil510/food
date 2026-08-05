package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.OrderItemDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.PlaceOrderRequest;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.exception.EmptyCartException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

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

    private OrderDTO sampleOrder() {
        return new OrderDTO(50L, 1L, "Spice Route", new BigDecimal("120.00"),
                OrderStatus.PLACED, LocalDateTime.of(2026, 8, 5, 12, 0));
    }

    @Test
    void placeOrder_returns201WithOrderConfirmation() throws Exception {
        when(orderService.placeOrder(USER_ID, 9L)).thenReturn(sampleOrder());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(9L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.totalAmount").value(120.00))
                .andExpect(jsonPath("$.status").value("PLACED"));

        verify(orderService).placeOrder(USER_ID, 9L);
    }

    @Test
    void placeOrder_returns400_whenAddressIdMissing() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_whenCartIsEmpty() throws Exception {
        when(orderService.placeOrder(USER_ID, 9L)).thenThrow(new EmptyCartException("Cart is empty"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(9L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns404_whenAddressDoesNotBelongToUser() throws Exception {
        when(orderService.placeOrder(USER_ID, 42L))
                .thenThrow(new AddressNotFoundException("Address not found: 42"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(42L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrders_returns200WithPagedHistory() throws Exception {
        PagedResponse<OrderDTO> page = new PagedResponse<>(List.of(sampleOrder()), 1, 1, 0);
        when(orderService.getUserOrders(USER_ID, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/orders").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getOrders_defaultsToPageZeroSizeTen() throws Exception {
        when(orderService.getUserOrders(USER_ID, 0, 10))
                .thenReturn(new PagedResponse<>(List.of(), 0, 0, 0));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());

        verify(orderService).getUserOrders(USER_ID, 0, 10);
    }

    @Test
    void getOrder_returns200WithDetail() throws Exception {
        OrderDetailDTO detail = new OrderDetailDTO(50L, "Spice Route", new BigDecimal("120.00"),
                OrderStatus.PLACED, LocalDateTime.of(2026, 8, 5, 12, 0),
                new AddressDTO(9L, "Home", "221B Baker Street", "Mumbai", "400001"),
                List.of(new OrderItemDTO(10L, "Samosa", 2, new BigDecimal("60.00"), new BigDecimal("120.00"))));
        when(orderService.getOrderById(USER_ID, 50L)).thenReturn(detail);

        mockMvc.perform(get("/api/orders/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Spice Route"))
                .andExpect(jsonPath("$.deliveryAddress.city").value("Mumbai"))
                .andExpect(jsonPath("$.items[0].foodItemName").value("Samosa"));
    }

    @Test
    void getOrder_returns404_whenOrderBelongsToAnotherUser() throws Exception {
        when(orderService.getOrderById(eq(USER_ID), eq(99L)))
                .thenThrow(new OrderNotFoundException("Order not found: 99"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }
}
