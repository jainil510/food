package com.foodrush.backend.service;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private OrderRepository orderRepository;

    private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository, foodItemRepository, orderRepository);
    }

    private Restaurant sampleRestaurant() {
        return Restaurant.builder()
                .id(1L)
                .name("Spice Route")
                .description("Authentic North Indian")
                .address("12 MG Road")
                .cuisineType("North Indian")
                .rating(new BigDecimal("4.5"))
                .imageUrl("https://example.com/spice-route.jpg")
                .build();
    }

    @Test
    void getAllRestaurants_returnsPagedResponseFromRepository() {
        Page<Restaurant> page = new PageImpl<>(java.util.List.of(sampleRestaurant()), PageRequest.of(0, 10), 1);
        when(restaurantRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<RestaurantDTO> result = restaurantService.getAllRestaurants(0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Spice Route");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.currentPage()).isEqualTo(0);
    }

    @Test
    void getRestaurantById_returnsDTO_whenFound() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(sampleRestaurant()));

        RestaurantDTO result = restaurantService.getRestaurantById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Spice Route");
    }

    @Test
    void getRestaurantById_throwsRestaurantNotFoundException_whenMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getRestaurantById(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void searchRestaurants_delegatesToNameSearchRepositoryMethod() {
        Page<Restaurant> page = new PageImpl<>(java.util.List.of(sampleRestaurant()), PageRequest.of(0, 10), 1);
        when(restaurantRepository.findByNameContainingIgnoreCase(eq("spice"), any(Pageable.class))).thenReturn(page);

        PagedResponse<RestaurantDTO> result = restaurantService.searchRestaurants("spice", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Spice Route");
    }

    @Test
    void filterByCuisine_delegatesToCuisineRepositoryMethod() {
        Page<Restaurant> page = new PageImpl<>(java.util.List.of(sampleRestaurant()), PageRequest.of(0, 10), 1);
        when(restaurantRepository.findByCuisineType(eq("North Indian"), any(Pageable.class))).thenReturn(page);

        PagedResponse<RestaurantDTO> result = restaurantService.filterByCuisine("North Indian", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).cuisineType()).isEqualTo("North Indian");
    }
}
