package com.foodrush.backend.controller;

import com.foodrush.backend.dto.AdminOrderDTO;
import com.foodrush.backend.dto.DashboardStatsDTO;
import com.foodrush.backend.dto.OrderStatusUpdateRequest;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.service.AdminOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping("/orders")
    public ResponseEntity<PagedResponse<AdminOrderDTO>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(adminOrderService.getAllOrders(page, size, status));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<AdminOrderDTO> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(adminOrderService.getOrderById(orderId));
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<AdminOrderDTO> updateOrderStatus(@PathVariable Long orderId,
                                                             @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(adminOrderService.updateOrderStatus(orderId, request.status()));
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(adminOrderService.getOrderStatistics());
    }
}
