package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Restores rule 4 ("an empty cart holds no restaurant") for cart lines removed by a bulk delete
     * rather than through the cart API (see FoodItemService.deleteFoodItem). flushAutomatically is
     * load-bearing: the caller deletes cart lines first via a derived-delete query, and those
     * deletes must be flushed to the database before this statement evaluates "c.items IS EMPTY" -
     * otherwise the bulk update runs against stale data and silently does nothing.
     * clearAutomatically then drops the now-stale managed Cart instances from the persistence
     * context so nothing re-caches the pre-update state. Repairing every empty cart rather than
     * only the one just affected is deliberate: "an empty cart holds no restaurant" is an
     * invariant of the table, so this call also self-heals any cart stranded by a previous delete
     * that ran before this fix existed.
     *
     * @return how many carts were released, so callers can log or assert on it
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Cart c SET c.restaurant = null WHERE c.restaurant IS NOT NULL AND c.items IS EMPTY")
    int clearRestaurantFromEmptyCarts();
}
