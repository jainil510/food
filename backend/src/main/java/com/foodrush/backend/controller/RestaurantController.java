package com.foodrush.backend.controller;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.service.RestaurantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RestaurantDTO>> getRestaurants(
            @RequestParam(required = false) String cuisine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<RestaurantDTO> response = (cuisine == null || cuisine.isBlank())
                ? restaurantService.getAllRestaurants(page, size)
                : restaurantService.filterByCuisine(cuisine, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDTO> getRestaurantById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getRestaurantById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<RestaurantDTO>> searchRestaurants(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(query, page, size));
    }
}
