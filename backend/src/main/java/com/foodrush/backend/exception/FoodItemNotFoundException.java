package com.foodrush.backend.exception;

public class FoodItemNotFoundException extends RuntimeException {

    public FoodItemNotFoundException(String message) {
        super(message);
    }
}
