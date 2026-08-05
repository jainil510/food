package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<OrderStatus> excludedStatuses);

    @EntityGraph(attributePaths = {"restaurant"})
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"restaurant", "address", "items", "items.foodItem"})
    Optional<Order> findByUserIdAndId(Long userId, Long id);
}
