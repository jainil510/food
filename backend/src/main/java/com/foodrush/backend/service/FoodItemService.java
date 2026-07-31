package com.foodrush.backend.service;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.MenuResponse;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.CategoryRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderItemRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;

    public FoodItemService(FoodItemRepository foodItemRepository,
                            RestaurantRepository restaurantRepository,
                            CategoryRepository categoryRepository,
                            OrderItemRepository orderItemRepository) {
        this.foodItemRepository = foodItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Read-only transaction is required: FoodItem.category is LAZY and the grouping below
     * dereferences it. Without an open session this throws LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<MenuResponse> getMenuByRestaurant(Long restaurantId, Long categoryId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException("Restaurant not found: " + restaurantId);
        }
        List<FoodItem> items = (categoryId == null)
                ? foodItemRepository.findByRestaurantIdAndIsAvailable(restaurantId, true)
                : foodItemRepository.findByRestaurantIdAndCategoryIdAndIsAvailable(restaurantId, categoryId, true);

        Map<String, List<FoodItem>> grouped = items.stream()
                .sorted(Comparator.comparing(FoodItem::getId))
                .collect(Collectors.groupingBy(item -> item.getCategory().getName(), TreeMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> new MenuResponse(
                        entry.getKey(),
                        entry.getValue().stream().map(FoodItemDTO::from).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FoodItemDTO getFoodItemById(Long id) {
        return FoodItemDTO.from(requireFoodItem(id));
    }

    private FoodItem requireFoodItem(Long id) {
        return foodItemRepository.findById(id)
                .orElseThrow(() -> new FoodItemNotFoundException("Food item not found: " + id));
    }
}
