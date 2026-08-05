package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderItem;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDTOTest {

    @Test
    void orderDTO_mapsRestaurantAndTotals() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Spice Route").build();
        Order order = Order.builder()
                .id(50L).restaurant(restaurant)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 12, 0)).build();

        OrderDTO dto = OrderDTO.from(order);

        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.restaurantId()).isEqualTo(1L);
        assertThat(dto.restaurantName()).isEqualTo("Spice Route");
        assertThat(dto.totalAmount()).isEqualByComparingTo("120.00");
        assertThat(dto.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void orderDetailDTO_mapsAddressAndItems() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Spice Route").build();
        Address address = Address.builder().id(9L).label("Home").fullAddress("221B Baker Street")
                .city("Mumbai").pincode("400001").build();
        FoodItem samosa = FoodItem.builder().id(10L).name("Samosa").build();
        Order order = Order.builder()
                .id(50L).restaurant(restaurant).address(address)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 12, 0))
                .items(List.of(OrderItem.builder()
                        .foodItem(samosa).quantity(2).priceAtOrder(new BigDecimal("60.00")).build()))
                .build();

        OrderDetailDTO dto = OrderDetailDTO.from(order);

        assertThat(dto.restaurantName()).isEqualTo("Spice Route");
        assertThat(dto.deliveryAddress().id()).isEqualTo(9L);
        assertThat(dto.deliveryAddress().city()).isEqualTo("Mumbai");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).foodItemName()).isEqualTo("Samosa");
        assertThat(dto.items().get(0).subtotal()).isEqualByComparingTo("120.00");
    }
}
