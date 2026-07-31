package com.foodrush.backend.repository;

import com.foodrush.backend.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    long deleteByRestaurantId(Long restaurantId);
}
