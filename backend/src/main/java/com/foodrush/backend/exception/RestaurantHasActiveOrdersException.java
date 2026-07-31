package com.foodrush.backend.exception;

public class RestaurantHasActiveOrdersException extends RuntimeException {

    public RestaurantHasActiveOrdersException(String message) {
        super(message);
    }
}
