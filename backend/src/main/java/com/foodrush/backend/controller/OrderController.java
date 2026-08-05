package com.foodrush.backend.controller;

import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.PlaceOrderRequest;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDTO> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(principal.getId(), request.addressId()));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderDTO>> getOrders(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getUserOrders(principal.getId(), page, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailDTO> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(principal.getId(), orderId));
    }
}
