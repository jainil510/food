package com.foodrush.backend.service;

import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

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
}
