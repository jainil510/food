package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * The entity graph is required, not an optimisation: Cart.restaurant, Cart.items and
     * CartItem.foodItem are all LAZY, and building a CartDTO dereferences every one of them.
     * Fetching them in one query avoids both LazyInitializationException outside a session
     * and an N+1 select per cart line.
     */
    @EntityGraph(attributePaths = {"restaurant", "items", "items.foodItem"})
    Optional<Cart> findByUserId(Long userId);
}
