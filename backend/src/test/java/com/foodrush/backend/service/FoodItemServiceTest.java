package com.foodrush.backend.service;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.MenuResponse;
import com.foodrush.backend.entity.Category;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.CategoryRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderItemRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodItemServiceTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private FoodItemService foodItemService;

    @BeforeEach
    void setUp() {
        foodItemService = new FoodItemService(
                foodItemRepository, restaurantRepository, categoryRepository, orderItemRepository);
    }

    private FoodItem foodItem(Long id, String name, Long categoryId, String categoryName, String price) {
        return FoodItem.builder()
                .id(id)
                .restaurant(Restaurant.builder().id(1L).name("Spice Route").build())
                .category(Category.builder().id(categoryId).name(categoryName).build())
                .name(name)
                .description(name + " description")
                .price(new BigDecimal(price))
                .imageUrl("https://example.com/" + id + ".jpg")
                .isAvailable(true)
                .build();
    }

    @Test
    void getMenuByRestaurant_groupsItemsByCategoryName_sortedByCategoryThenItemId() {
        // Deliberately out of order: Desserts before Appetizers, and item 3 before items 1 and 2.
        when(foodItemRepository.findByRestaurantIdAndIsAvailable(1L, true)).thenReturn(List.of(
                foodItem(3L, "Gulab Jamun", 20L, "Desserts", "80.00"),
                foodItem(2L, "Paneer Tikka", 10L, "Appetizers", "240.00"),
                foodItem(1L, "Samosa", 10L, "Appetizers", "60.00")));
        when(restaurantRepository.existsById(1L)).thenReturn(true);

        List<MenuResponse> result = foodItemService.getMenuByRestaurant(1L, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).categoryName()).isEqualTo("Appetizers");
        assertThat(result.get(0).items()).extracting(FoodItemDTO::name)
                .containsExactly("Samosa", "Paneer Tikka");
        assertThat(result.get(1).categoryName()).isEqualTo("Desserts");
        assertThat(result.get(1).items()).extracting(FoodItemDTO::name)
                .containsExactly("Gulab Jamun");
    }

    @Test
    void getMenuByRestaurant_requestsOnlyAvailableItems() {
        when(restaurantRepository.existsById(1L)).thenReturn(true);
        when(foodItemRepository.findByRestaurantIdAndIsAvailable(1L, true)).thenReturn(List.of());

        foodItemService.getMenuByRestaurant(1L, null);

        // The `true` argument is the guarantee that unavailable items never reach the public menu.
        verify(foodItemRepository).findByRestaurantIdAndIsAvailable(1L, true);
    }

    @Test
    void getMenuByRestaurant_filtersToSingleCategory_whenCategoryIdProvided() {
        when(restaurantRepository.existsById(1L)).thenReturn(true);
        when(foodItemRepository.findByRestaurantIdAndCategoryIdAndIsAvailable(1L, 10L, true)).thenReturn(List.of(
                foodItem(1L, "Samosa", 10L, "Appetizers", "60.00")));

        List<MenuResponse> result = foodItemService.getMenuByRestaurant(1L, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("Appetizers");
        assertThat(result.get(0).items()).hasSize(1);
        verify(foodItemRepository).findByRestaurantIdAndCategoryIdAndIsAvailable(1L, 10L, true);
    }

    @Test
    void getMenuByRestaurant_returnsEmptyList_whenRestaurantHasNoItems() {
        when(restaurantRepository.existsById(1L)).thenReturn(true);
        when(foodItemRepository.findByRestaurantIdAndIsAvailable(1L, true)).thenReturn(List.of());

        List<MenuResponse> result = foodItemService.getMenuByRestaurant(1L, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getMenuByRestaurant_throwsRestaurantNotFoundException_whenRestaurantMissing() {
        when(restaurantRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> foodItemService.getMenuByRestaurant(99L, null))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getFoodItemById_returnsDTO_whenFound() {
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(
                foodItem(1L, "Samosa", 10L, "Appetizers", "60.00")));

        FoodItemDTO result = foodItemService.getFoodItemById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Samosa");
        assertThat(result.price()).isEqualByComparingTo("60.00");
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    void getFoodItemById_throwsFoodItemNotFoundException_whenMissing() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.getFoodItemById(99L))
                .isInstanceOf(FoodItemNotFoundException.class);
    }
}
