package com.foodrush.backend.service;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.dto.RestaurantRequest;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.RestaurantHasActiveOrdersException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RestaurantService {

    private static final List<OrderStatus> TERMINAL_ORDER_STATUSES = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final RestaurantRepository restaurantRepository;
    private final FoodItemRepository foodItemRepository;
    private final OrderRepository orderRepository;

    public RestaurantService(RestaurantRepository restaurantRepository,
                              FoodItemRepository foodItemRepository,
                              OrderRepository orderRepository) {
        this.restaurantRepository = restaurantRepository;
        this.foodItemRepository = foodItemRepository;
        this.orderRepository = orderRepository;
    }

    public PagedResponse<RestaurantDTO> getAllRestaurants(int page, int size) {
        Page<Restaurant> result = restaurantRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public RestaurantDTO getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        return RestaurantDTO.from(restaurant);
    }

    public PagedResponse<RestaurantDTO> searchRestaurants(String query, int page, int size) {
        Page<Restaurant> result = restaurantRepository.findByNameContainingIgnoreCase(query, PageRequest.of(page, size, Sort.by("id")));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public PagedResponse<RestaurantDTO> filterByCuisine(String cuisineType, int page, int size) {
        Page<Restaurant> result = restaurantRepository.findByCuisineType(cuisineType, PageRequest.of(page, size, Sort.by("id")));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public RestaurantDTO createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.name())
                .description(request.description())
                .address(request.address())
                .cuisineType(request.cuisineType())
                .rating(request.rating())
                .imageUrl(request.imageUrl())
                .build();
        return RestaurantDTO.from(restaurantRepository.save(restaurant));
    }

    public RestaurantDTO updateRestaurant(Long id, RestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        restaurant.setName(request.name());
        restaurant.setDescription(request.description());
        restaurant.setAddress(request.address());
        restaurant.setCuisineType(request.cuisineType());
        restaurant.setRating(request.rating());
        restaurant.setImageUrl(request.imageUrl());
        return RestaurantDTO.from(restaurantRepository.save(restaurant));
    }

    @Transactional
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        if (orderRepository.existsByRestaurantIdAndStatusNotIn(id, TERMINAL_ORDER_STATUSES)) {
            throw new RestaurantHasActiveOrdersException("Cannot delete restaurant with active orders: " + id);
        }
        foodItemRepository.deleteByRestaurantId(id);
        restaurantRepository.delete(restaurant);
    }
}
