package com.foodrush.backend.repository;

import com.foodrush.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Used when a food item is hard-deleted: cart_items.food_item_id is a foreign key, so the
     * lines must go first. Cart reads all go through Cart.items instead.
     */
    long deleteByFoodItemId(Long foodItemId);
}
