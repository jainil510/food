package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Page<Restaurant> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Restaurant> findByCuisineType(String cuisineType, Pageable pageable);
}
