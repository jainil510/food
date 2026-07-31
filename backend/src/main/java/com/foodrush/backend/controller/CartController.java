package com.foodrush.backend.controller;

import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.dto.UpdateCartItemRequest;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint returns the full cart so the client always holds a recalculated total and
 * never needs a follow-up GET. That is also why the deletes return 200 with a body, not 204.
 *
 * Do not add a GET /api/cart/{something} mapping here: the test-only ProbeController maps
 * GET /api/cart/probe, and the two would collide ambiguously under @SpringBootTest.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartDTO> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDTO> addItem(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItemToCart(principal.getId(), request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartDTO> updateItem(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long cartItemId,
                                               @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(
                cartService.updateCartItem(principal.getId(), cartItemId, request.quantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartDTO> removeItem(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long cartItemId) {
        return ResponseEntity.ok(cartService.removeCartItem(principal.getId(), cartItemId));
    }

    @DeleteMapping
    public ResponseEntity<CartDTO> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.clearCart(principal.getId()));
    }
}
