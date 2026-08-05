package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.AdminOrderDTO;
import com.foodrush.backend.dto.DashboardStatsDTO;
import com.foodrush.backend.dto.OrderItemDTO;
import com.foodrush.backend.dto.OrderStatusUpdateRequest;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.exception.InvalidOrderStatusTransitionException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.service.AdminOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private AdminOrderService adminOrderService;

    private AdminOrderDTO sampleOrder() {
        return new AdminOrderDTO(50L, 7L, "Asha", "asha@foodrush.com", 1L, "Spice Route",
                new BigDecimal("120.00"), OrderStatus.PLACED, LocalDateTime.of(2026, 8, 5, 12, 0),
                new AddressDTO(9L, "Home", "221B Baker Street", "Mumbai", "400001"),
                List.of(new OrderItemDTO(10L, "Samosa", 2, new BigDecimal("60.00"), new BigDecimal("120.00"))));
    }

    @Test
    void getOrders_returns200WithPagedOrders() throws Exception {
        PagedResponse<AdminOrderDTO> page = new PagedResponse<>(List.of(sampleOrder()), 1, 1, 0);
        when(adminOrderService.getAllOrders(0, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/admin/orders").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[0].userEmail").value("asha@foodrush.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getOrders_defaultsToPageZeroSizeTwenty() throws Exception {
        when(adminOrderService.getAllOrders(0, 20, null)).thenReturn(new PagedResponse<>(List.of(), 0, 0, 0));

        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk());

        verify(adminOrderService).getAllOrders(0, 20, null);
    }

    @Test
    void getOrders_withStatusFilter_passesStatusThrough() throws Exception {
        when(adminOrderService.getAllOrders(eq(0), eq(20), eq(OrderStatus.PLACED)))
                .thenReturn(new PagedResponse<>(List.of(sampleOrder()), 1, 1, 0));

        mockMvc.perform(get("/api/admin/orders").param("status", "PLACED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PLACED"));

        verify(adminOrderService).getAllOrders(0, 20, OrderStatus.PLACED);
    }

    @Test
    void getOrder_returns200WithDetail() throws Exception {
        when(adminOrderService.getOrderById(50L)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/admin/orders/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Spice Route"))
                .andExpect(jsonPath("$.items[0].foodItemName").value("Samosa"));
    }

    @Test
    void getOrder_returns404_whenOrderDoesNotExist() throws Exception {
        when(adminOrderService.getOrderById(99L)).thenThrow(new OrderNotFoundException("Order not found: 99"));

        mockMvc.perform(get("/api/admin/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateOrderStatus_returns200WithUpdatedOrder() throws Exception {
        AdminOrderDTO confirmed = sampleOrder();
        when(adminOrderService.updateOrderStatus(50L, OrderStatus.CONFIRMED))
                .thenReturn(new AdminOrderDTO(confirmed.id(), confirmed.userId(), confirmed.userName(),
                        confirmed.userEmail(), confirmed.restaurantId(), confirmed.restaurantName(),
                        confirmed.totalAmount(), OrderStatus.CONFIRMED, confirmed.createdAt(),
                        confirmed.deliveryAddress(), confirmed.items()));

        mockMvc.perform(put("/api/admin/orders/50/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(adminOrderService).updateOrderStatus(50L, OrderStatus.CONFIRMED);
    }

    @Test
    void updateOrderStatus_returns400_whenStatusMissing() throws Exception {
        mockMvc.perform(put("/api/admin/orders/50/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateOrderStatus_returns409_whenTransitionInvalid() throws Exception {
        when(adminOrderService.updateOrderStatus(50L, OrderStatus.DELIVERED))
                .thenThrow(new InvalidOrderStatusTransitionException("Cannot transition order from PLACED to DELIVERED"));

        mockMvc.perform(put("/api/admin/orders/50/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.DELIVERED))))
                .andExpect(status().isConflict());
    }

    @Test
    void updateOrderStatus_returns404_whenOrderDoesNotExist() throws Exception {
        when(adminOrderService.updateOrderStatus(99L, OrderStatus.CONFIRMED))
                .thenThrow(new OrderNotFoundException("Order not found: 99"));

        mockMvc.perform(put("/api/admin/orders/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDashboardStats_returns200WithStatistics() throws Exception {
        when(adminOrderService.getOrderStatistics())
                .thenReturn(new DashboardStatsDTO(42L, 5L, 8L, new BigDecimal("15000.00")));

        mockMvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(42))
                .andExpect(jsonPath("$.todayOrders").value(5))
                .andExpect(jsonPath("$.pendingOrders").value(8))
                .andExpect(jsonPath("$.revenue").value(15000.00));
    }
}
