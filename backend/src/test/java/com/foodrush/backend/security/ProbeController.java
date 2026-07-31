package com.foodrush.backend.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProbeController {

    @GetMapping("/api/auth/probe")
    String authProbe() {
        return "ok";
    }

    @PostMapping("/api/auth/probe")
    String authProbePost() {
        return "ok";
    }

    @GetMapping("/api/restaurants/probe")
    String restaurantsProbe() {
        return "ok";
    }

    @GetMapping("/api/categories/probe")
    String categoriesProbe() {
        return "ok";
    }

    @GetMapping("/api/food-items/probe")
    String foodItemsProbe() {
        return "ok";
    }

    @GetMapping("/api/cart/probe")
    String cartProbe() {
        return "ok";
    }

    @GetMapping("/api/admin/probe")
    String adminProbe() {
        return "ok";
    }
}
