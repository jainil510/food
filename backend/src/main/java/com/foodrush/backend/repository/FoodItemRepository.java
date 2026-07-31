package com.foodrush.backend.repository;

import com.foodrush.backend.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    long deleteByRestaurantId(Long restaurantId);

    List<FoodItem> findByRestaurantIdAndIsAvailable(Long restaurantId, boolean isAvailable);

    List<FoodItem> findByRestaurantIdAndCategoryIdAndIsAvailable(Long restaurantId, Long categoryId, boolean isAvailable);
}
