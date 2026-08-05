package com.foodrush.backend.exception;

public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(String message) {
        super(message);
    }
}
