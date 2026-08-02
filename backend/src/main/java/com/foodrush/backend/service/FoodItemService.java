package com.foodrush.backend.service;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.FoodItemRequest;
import com.foodrush.backend.dto.MenuResponse;
import com.foodrush.backend.entity.Category;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.CategoryNotFoundException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.CartItemRepository;
import com.foodrush.backend.repository.CartRepository;
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
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;

    public FoodItemService(FoodItemRepository foodItemRepository,
                            RestaurantRepository restaurantRepository,
                            CategoryRepository categoryRepository,
                            OrderItemRepository orderItemRepository,
                            CartItemRepository cartItemRepository,
                            CartRepository cartRepository) {
        this.foodItemRepository = foodItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
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

    @Transactional
    public FoodItemDTO createFoodItem(FoodItemRequest request) {
        Restaurant restaurant = requireRestaurant(request.restaurantId());
        Category category = requireCategory(request.categoryId());
        FoodItem foodItem = FoodItem.builder()
                .restaurant(restaurant)
                .category(category)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .isAvailable(true)
                .build();
        return FoodItemDTO.from(foodItemRepository.save(foodItem));
    }

    @Transactional
    public FoodItemDTO updateFoodItem(Long id, FoodItemRequest request) {
        FoodItem foodItem = requireFoodItem(id);
        foodItem.setRestaurant(requireRestaurant(request.restaurantId()));
        foodItem.setCategory(requireCategory(request.categoryId()));
        foodItem.setName(request.name());
        foodItem.setDescription(request.description());
        foodItem.setPrice(request.price());
        foodItem.setImageUrl(request.imageUrl());
        // isAvailable is deliberately untouched - it is owned by toggleAvailability/deleteFoodItem.
        return FoodItemDTO.from(foodItemRepository.save(foodItem));
    }

    /**
     * Soft-deletes (marks unavailable) any item that already appears in order history, so past
     * orders keep referring to a real row. Items with no order history are removed outright -
     * and because cart_items.food_item_id is a foreign key, any cart lines holding the item
     * must go first or the delete fails with a constraint violation.
     *
     * Purging lines this way bypasses CartService.saveAndConvert, which is where rule 4 (an
     * empty cart holds no restaurant) is normally enforced. Without the repository call below, a
     * user whose only cart line was deleted here keeps a cart that looks empty but is still
     * pinned to the old restaurant, and gets rejected when adding an item from anywhere else.
     */
    @Transactional
    public void deleteFoodItem(Long id) {
        FoodItem foodItem = requireFoodItem(id);
        if (orderItemRepository.existsByFoodItemId(id)) {
            foodItem.setAvailable(false);
            foodItemRepository.save(foodItem);
            return;
        }
        cartItemRepository.deleteByFoodItemId(id);
        cartRepository.clearRestaurantFromEmptyCarts();
        foodItemRepository.delete(foodItem);
    }

    @Transactional
    public FoodItemDTO toggleAvailability(Long id) {
        FoodItem foodItem = requireFoodItem(id);
        foodItem.setAvailable(!foodItem.isAvailable());
        return FoodItemDTO.from(foodItemRepository.save(foodItem));
    }

    private FoodItem requireFoodItem(Long id) {
        return foodItemRepository.findById(id)
                .orElseThrow(() -> new FoodItemNotFoundException("Food item not found: " + id));
    }

    private Restaurant requireRestaurant(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));
    }
}
