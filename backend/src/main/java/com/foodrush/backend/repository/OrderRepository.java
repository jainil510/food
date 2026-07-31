package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<OrderStatus> excludedStatuses);
}
