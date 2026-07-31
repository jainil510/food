package com.foodrush.backend.repository;

import com.foodrush.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByFoodItemId(Long foodItemId);
}
