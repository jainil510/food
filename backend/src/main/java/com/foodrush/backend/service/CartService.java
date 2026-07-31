package com.foodrush.backend.service;

import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CartService {

    private static final String DIFFERENT_RESTAURANT_MESSAGE =
            "Cart can only contain items from one restaurant. Clear cart first?";

    private final CartRepository cartRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                        FoodItemRepository foodItemRepository,
                        UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.foodItemRepository = foodItemRepository;
        this.userRepository = userRepository;
    }

    /**
     * Read-only on purpose: a user with no cart row gets an empty representation rather than
     * a freshly inserted row.
     */
    @Transactional(readOnly = true)
    public CartDTO getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(CartDTO::from)
                .orElseGet(CartDTO::empty);
    }

    /**
     * Validation order matters: existence, then availability, then the single-restaurant rule.
     * Availability is a property of the item alone, while a restaurant conflict depends on
     * cart state the user can clear - so the more fundamental problem is reported first.
     */
    @Transactional
    public CartDTO addItemToCart(Long userId, AddToCartRequest request) {
        FoodItem foodItem = foodItemRepository.findById(request.foodItemId())
                .orElseThrow(() -> new FoodItemNotFoundException(
                        "Food item not found: " + request.foodItemId()));
        if (!foodItem.isAvailable()) {
            throw new CartConflictException("Food item is not available: " + foodItem.getName());
        }

        Cart cart = getOrCreateCart(userId);
        Restaurant cartRestaurant = cart.getRestaurant();
        if (cartRestaurant != null && !cartRestaurant.getId().equals(foodItem.getRestaurant().getId())) {
            throw new CartConflictException(DIFFERENT_RESTAURANT_MESSAGE);
        }
        cart.setRestaurant(foodItem.getRestaurant());

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(item -> item.getFoodItem().getId().equals(foodItem.getId()))
                .findFirst();
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.quantity());
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .foodItem(foodItem)
                    .quantity(request.quantity())
                    .build());
        }
        return CartDTO.from(cartRepository.save(cart));
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Authenticated user not found: " + userId));
                    return cartRepository.save(Cart.builder().user(user).build());
                });
    }
}
