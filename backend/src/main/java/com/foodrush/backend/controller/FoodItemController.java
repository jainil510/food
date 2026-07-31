package com.foodrush.backend.controller;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.MenuResponse;
import com.foodrush.backend.service.FoodItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FoodItemController {

    private final FoodItemService foodItemService;

    public FoodItemController(FoodItemService foodItemService) {
        this.foodItemService = foodItemService;
    }

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuResponse>> getMenu(
            @PathVariable Long restaurantId,
            @RequestParam(name = "category", required = false) Long categoryId) {
        return ResponseEntity.ok(foodItemService.getMenuByRestaurant(restaurantId, categoryId));
    }

    @GetMapping("/api/food-items/{id}")
    public ResponseEntity<FoodItemDTO> getFoodItemById(@PathVariable Long id) {
        return ResponseEntity.ok(foodItemService.getFoodItemById(id));
    }
}
