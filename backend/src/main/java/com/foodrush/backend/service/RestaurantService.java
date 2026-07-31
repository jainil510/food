package com.foodrush.backend.service;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class RestaurantService {

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
        Page<Restaurant> result = restaurantRepository.findAll(PageRequest.of(page, size));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public RestaurantDTO getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        return RestaurantDTO.from(restaurant);
    }

    public PagedResponse<RestaurantDTO> searchRestaurants(String query, int page, int size) {
        Page<Restaurant> result = restaurantRepository.findByNameContainingIgnoreCase(query, PageRequest.of(page, size));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public PagedResponse<RestaurantDTO> filterByCuisine(String cuisineType, int page, int size) {
        Page<Restaurant> result = restaurantRepository.findByCuisineType(cuisineType, PageRequest.of(page, size));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }
}
