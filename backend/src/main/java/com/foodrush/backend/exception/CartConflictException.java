package com.foodrush.backend.exception;

/**
 * A cart operation that is valid input but conflicts with cart or catalogue state: adding an
 * item from a second restaurant, or adding an item that is no longer available. Maps to 409.
 */
public class CartConflictException extends RuntimeException {

    public CartConflictException(String message) {
        super(message);
    }
}
