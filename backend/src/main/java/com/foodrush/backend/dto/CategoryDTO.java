package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Category;

public record CategoryDTO(Long id, String name) {

    public static CategoryDTO from(Category category) {
        return new CategoryDTO(category.getId(), category.getName());
    }
}
