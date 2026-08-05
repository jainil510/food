package com.foodrush.backend.service;

import com.foodrush.backend.dto.AdminOrderDTO;
import com.foodrush.backend.dto.DashboardStatsDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.exception.InvalidOrderStatusTransitionException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class AdminOrderService {

    private static final Set<OrderStatus> TERMINAL_STATUSES = EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    /**
     * Only forward, one-step moves through the fulfilment pipeline are valid; PLACED/CONFIRMED
     * can also be cancelled. DELIVERED and CANCELLED are terminal - no further transitions.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PLACED, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.OUT_FOR_DELIVERY));
        ALLOWED_TRANSITIONS.put(OrderStatus.OUT_FOR_DELIVERY, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;

    public AdminOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminOrderDTO> getAllOrders(int page, int size, OrderStatus status) {
        Page<Order> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : orderRepository.findAllByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
        return PagedResponse.from(orders.map(AdminOrderDTO::from));
    }

    @Transactional(readOnly = true)
    public AdminOrderDTO getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return AdminOrderDTO.from(order);
    }

    @Transactional
    public AdminOrderDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!ALLOWED_TRANSITIONS.get(order.getStatus()).contains(newStatus)) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot transition order from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        return AdminOrderDTO.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getOrderStatistics() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return new DashboardStatsDTO(
                orderRepository.count(),
                orderRepository.countByCreatedAtAfter(startOfToday),
                orderRepository.countByStatusNotIn(TERMINAL_STATUSES),
                orderRepository.sumTotalAmountByStatusNot(OrderStatus.CANCELLED));
    }
}
