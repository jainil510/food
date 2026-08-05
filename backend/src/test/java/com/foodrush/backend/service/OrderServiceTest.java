package com.foodrush.backend.service;

import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderItem;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.EmptyCartException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.repository.AddressRepository;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AddressRepository addressRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, addressRepository);
    }

    static User user(Long id) {
        return User.builder().id(id).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build();
    }

    static Restaurant restaurant(Long id, String name) {
        return Restaurant.builder().id(id).name(name).build();
    }

    static FoodItem foodItem(Long id, String name, String price, Restaurant restaurant, boolean available) {
        return FoodItem.builder().id(id).name(name).price(new BigDecimal(price))
                .restaurant(restaurant).isAvailable(available).build();
    }

    static Cart cartWithItems(User user, Restaurant restaurant, CartItem... items) {
        return Cart.builder().id(100L).user(user).restaurant(restaurant)
                .items(new ArrayList<>(List.of(items))).build();
    }

    static CartItem cartItem(FoodItem foodItem, int quantity) {
        return CartItem.builder().id(1L).foodItem(foodItem).quantity(quantity).build();
    }

    static Address address(Long id, User user) {
        return Address.builder().id(id).user(user).label("Home")
                .fullAddress("221B Baker Street").city("Mumbai").pincode("400001").build();
    }

    @Test
    void placeOrder_createsOrderFromCartAndClearsCart() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Cart cart = cartWithItems(asha, spiceRoute, cartItem(samosa, 2));
        Address address = address(9L, asha);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 9L)).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(50L);
            return order;
        });

        OrderDTO result = orderService.placeOrder(USER_ID, 9L);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.totalAmount()).isEqualByComparingTo("120.00");
        assertThat(result.status()).isEqualTo(OrderStatus.PLACED);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getPriceAtOrder()).isEqualByComparingTo("60.00");
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getAddress()).isEqualTo(address);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getRestaurant()).isNull();
        verify(cartRepository).save(cart);
    }

    @Test
    void placeOrder_throwsEmptyCart_whenCartHasNoItems() {
        Cart cart = cartWithItems(user(USER_ID), null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 9L))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_throwsEmptyCart_whenUserHasNoCartRow() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 9L))
                .isInstanceOf(EmptyCartException.class);
    }

    @Test
    void placeOrder_throwsAddressNotFound_whenAddressDoesNotBelongToUser() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cartWithItems(asha, spiceRoute,
                cartItem(foodItem(10L, "Samosa", "60.00", spiceRoute, true), 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 42L))
                .isInstanceOf(AddressNotFoundException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_throwsCartConflict_whenAnItemBecameUnavailable() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cartWithItems(asha, spiceRoute,
                cartItem(foodItem(10L, "Samosa", "60.00", spiceRoute, false), 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 9L)).thenReturn(Optional.of(address(9L, asha)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 9L))
                .isInstanceOf(CartConflictException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_snapshotsCurrentPrice_notALaterPrice() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Cart cart = cartWithItems(asha, spiceRoute, cartItem(samosa, 3));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 9L)).thenReturn(Optional.of(address(9L, asha)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.placeOrder(USER_ID, 9L);
        samosa.setPrice(new BigDecimal("999.00"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getPriceAtOrder()).isEqualByComparingTo("60.00");
    }

    @Test
    void getUserOrders_returnsPagedHistory() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Order order = Order.builder().id(50L).restaurant(spiceRoute)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED).build();
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any())).thenReturn(page);

        PagedResponse<OrderDTO> result = orderService.getUserOrders(USER_ID, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(50L);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getOrderById_returnsDetail_whenOrderBelongsToUser() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Address address = address(9L, asha);
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Order order = Order.builder().id(50L).restaurant(spiceRoute).address(address)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED)
                .items(List.of(OrderItem.builder()
                        .foodItem(samosa).quantity(2).priceAtOrder(new BigDecimal("60.00")).build()))
                .build();
        when(orderRepository.findByUserIdAndId(USER_ID, 50L)).thenReturn(Optional.of(order));

        OrderDetailDTO result = orderService.getOrderById(USER_ID, 50L);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.deliveryAddress().id()).isEqualTo(9L);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void getOrderById_throwsOrderNotFound_whenOrderBelongsToAnotherUser() {
        when(orderRepository.findByUserIdAndId(USER_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(USER_ID, 99L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
