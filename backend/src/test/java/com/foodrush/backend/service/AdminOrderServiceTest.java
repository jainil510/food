package com.foodrush.backend.service;

import com.foodrush.backend.dto.AdminOrderDTO;
import com.foodrush.backend.dto.DashboardStatsDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderItem;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.InvalidOrderStatusTransitionException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private AdminOrderService adminOrderService;

    @BeforeEach
    void setUp() {
        adminOrderService = new AdminOrderService(orderRepository);
    }

    static User user(Long id) {
        return User.builder().id(id).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build();
    }

    static Restaurant restaurant(Long id, String name) {
        return Restaurant.builder().id(id).name(name).build();
    }

    static Address address(Long id) {
        return Address.builder().id(id).label("Home")
                .fullAddress("221B Baker Street").city("Mumbai").pincode("400001").build();
    }

    static FoodItem foodItem(Long id, String name, Restaurant restaurant) {
        return FoodItem.builder().id(id).name(name).price(new BigDecimal("60.00"))
                .restaurant(restaurant).isAvailable(true).build();
    }

    static Order order(Long id, OrderStatus status, String amount) {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        return Order.builder().id(id).user(user(7L)).restaurant(spiceRoute)
                .address(address(9L))
                .totalAmount(new BigDecimal(amount)).status(status)
                .items(List.of(OrderItem.builder()
                        .foodItem(foodItem(10L, "Samosa", spiceRoute))
                        .quantity(2).priceAtOrder(new BigDecimal("60.00")).build()))
                .build();
    }

    @Test
    void getAllOrders_noStatusFilter_returnsAllOrdersAcrossUsers() {
        Order order = order(50L, OrderStatus.PLACED, "120.00");
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        when(orderRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        PagedResponse<AdminOrderDTO> result = adminOrderService.getAllOrders(0, 20, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(50L);
        assertThat(result.content().get(0).userId()).isEqualTo(7L);
        assertThat(result.content().get(0).userEmail()).isEqualTo("asha@foodrush.com");
        verify(orderRepository, never()).findAllByStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getAllOrders_withStatusFilter_delegatesToFilteredQuery() {
        Order order = order(50L, OrderStatus.CONFIRMED, "120.00");
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        when(orderRepository.findAllByStatusOrderByCreatedAtDesc(eq(OrderStatus.CONFIRMED), any())).thenReturn(page);

        PagedResponse<AdminOrderDTO> result = adminOrderService.getAllOrders(0, 20, OrderStatus.CONFIRMED);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void getOrderById_returnsDetail_withUserAndRestaurantInfo() {
        Order order = order(50L, OrderStatus.PLACED, "120.00");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        AdminOrderDTO result = adminOrderService.getOrderById(50L);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.restaurantName()).isEqualTo("Spice Route");
        assertThat(result.deliveryAddress().city()).isEqualTo("Mumbai");
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void getOrderById_throwsOrderNotFound_whenMissing() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PLACED", "CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY"})
    void updateOrderStatus_advancingToNextValidStatus_succeeds(OrderStatus current) {
        OrderStatus next = switch (current) {
            case PLACED -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.PREPARING;
            case PREPARING -> OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> OrderStatus.DELIVERED;
            default -> throw new IllegalStateException("unreachable");
        };
        Order order = order(50L, current, "120.00");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminOrderDTO result = adminOrderService.updateOrderStatus(50L, next);

        assertThat(result.status()).isEqualTo(next);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(next);
    }

    @Test
    void updateOrderStatus_placedToConfirmed_thenCancelled_isAllowed() {
        Order order = order(50L, OrderStatus.CONFIRMED, "120.00");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminOrderDTO result = adminOrderService.updateOrderStatus(50L, OrderStatus.CANCELLED);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateOrderStatus_fromDelivered_throwsInvalidTransition_becauseTerminal() {
        Order order = order(50L, OrderStatus.DELIVERED, "120.00");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderService.updateOrderStatus(50L, OrderStatus.PLACED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_skippingAheadFromPlacedToDelivered_throwsInvalidTransition() {
        Order order = order(50L, OrderStatus.PLACED, "120.00");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderService.updateOrderStatus(50L, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_throwsOrderNotFound_whenMissing() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.updateOrderStatus(99L, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getOrderStatistics_aggregatesCountsAndRevenue_excludingCancelledFromRevenue() {
        when(orderRepository.count()).thenReturn(42L);
        when(orderRepository.countByCreatedAtAfter(any())).thenReturn(5L);
        when(orderRepository.countByStatusNotIn(Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED))).thenReturn(8L);
        when(orderRepository.sumTotalAmountByStatusNot(OrderStatus.CANCELLED)).thenReturn(new BigDecimal("15000.00"));

        DashboardStatsDTO result = adminOrderService.getOrderStatistics();

        assertThat(result.totalOrders()).isEqualTo(42L);
        assertThat(result.todayOrders()).isEqualTo(5L);
        assertThat(result.pendingOrders()).isEqualTo(8L);
        assertThat(result.revenue()).isEqualByComparingTo("15000.00");
    }
}
