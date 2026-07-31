package com.foodrush.backend.dto;

import java.util.List;

public record MenuResponse(String categoryName, List<FoodItemDTO> items) {
}
