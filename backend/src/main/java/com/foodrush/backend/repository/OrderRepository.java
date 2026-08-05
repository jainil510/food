package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<OrderStatus> excludedStatuses);

    @EntityGraph(attributePaths = {"restaurant"})
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"restaurant", "address", "items", "items.foodItem"})
    Optional<Order> findByUserIdAndId(Long userId, Long id);

    @EntityGraph(attributePaths = {"user", "restaurant"})
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "restaurant"})
    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "restaurant", "address", "items", "items.foodItem"})
    Optional<Order> findById(Long id);

    long countByCreatedAtAfter(LocalDateTime createdAtAfter);

    long countByStatusNotIn(Collection<OrderStatus> excludedStatuses);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status <> :excludedStatus")
    BigDecimal sumTotalAmountByStatusNot(OrderStatus excludedStatus);
}
