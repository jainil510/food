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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartRepository cartRepository;

    private FoodItemService foodItemService;

    @BeforeEach
    void setUp() {
        foodItemService = new FoodItemService(
                foodItemRepository, restaurantRepository, categoryRepository, orderItemRepository,
                cartItemRepository, cartRepository);
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

    private FoodItemRequest sampleRequest() {
        return new FoodItemRequest(1L, 10L, "Samosa", "Crispy potato pastry",
                new BigDecimal("60.00"), "https://example.com/1.jpg");
    }

    @Test
    void createFoodItem_savesWithAvailabilityTrue_andReturnsDTO() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Spice Route").build();
        Category category = Category.builder().id(10L).name("Appetizers").build();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(invocation -> {
            FoodItem saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        FoodItemDTO result = foodItemService.createFoodItem(sampleRequest());

        ArgumentCaptor<FoodItem> captor = ArgumentCaptor.forClass(FoodItem.class);
        verify(foodItemRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Samosa");
        assertThat(captor.getValue().getRestaurant()).isSameAs(restaurant);
        assertThat(captor.getValue().getCategory()).isSameAs(category);
        assertThat(captor.getValue().isAvailable()).isTrue();
        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    void createFoodItem_throwsRestaurantNotFoundException_whenRestaurantMissing() {
        FoodItemRequest request = new FoodItemRequest(99L, 10L, "Samosa", null,
                new BigDecimal("60.00"), null);
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.createFoodItem(request))
                .isInstanceOf(RestaurantNotFoundException.class);

        verify(foodItemRepository, never()).save(any());
    }

    @Test
    void createFoodItem_throwsCategoryNotFoundException_whenCategoryMissing() {
        FoodItemRequest request = new FoodItemRequest(1L, 77L, "Samosa", null,
                new BigDecimal("60.00"), null);
        when(restaurantRepository.findById(1L))
                .thenReturn(Optional.of(Restaurant.builder().id(1L).name("Spice Route").build()));
        when(categoryRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.createFoodItem(request))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(foodItemRepository, never()).save(any());
    }

    @Test
    void updateFoodItem_updatesFieldsAndPreservesAvailability_whenFound() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        existing.setAvailable(false);
        Restaurant restaurant = Restaurant.builder().id(1L).name("Spice Route").build();
        Category category = Category.builder().id(20L).name("Desserts").build();
        FoodItemRequest request = new FoodItemRequest(1L, 20L, "Gulab Jamun", "Syrup soaked",
                new BigDecimal("80.00"), "https://example.com/updated.jpg");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(category));
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemDTO result = foodItemService.updateFoodItem(1L, request);

        assertThat(result.name()).isEqualTo("Gulab Jamun");
        assertThat(result.description()).isEqualTo("Syrup soaked");
        assertThat(result.price()).isEqualByComparingTo("80.00");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/updated.jpg");
        // Availability is owned by the PATCH endpoint, so an update must not silently re-enable it.
        assertThat(result.isAvailable()).isFalse();
        assertThat(existing.getCategory()).isSameAs(category);
    }

    @Test
    void updateFoodItem_throwsFoodItemNotFoundException_whenMissing() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.updateFoodItem(99L, sampleRequest()))
                .isInstanceOf(FoodItemNotFoundException.class);

        verify(foodItemRepository, never()).save(any());
    }

    @Test
    void deleteFoodItem_softDeletesBySettingUnavailable_whenOrderHistoryExists() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(true);

        foodItemService.deleteFoodItem(1L);

        assertThat(existing.isAvailable()).isFalse();
        verify(foodItemRepository).save(existing);
        verify(foodItemRepository, never()).delete(any());
    }

    @Test
    void deleteFoodItem_hardDeletes_whenNoOrderHistory() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(false);

        foodItemService.deleteFoodItem(1L);

        verify(foodItemRepository).delete(existing);
        verify(foodItemRepository, never()).save(any());
    }

    @Test
    void deleteFoodItem_purgesCartLinesBeforeHardDeleting() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(false);

        foodItemService.deleteFoodItem(1L);

        // cart_items.food_item_id is an FK: without this the delete raises
        // DataIntegrityViolationException and the admin sees a 409.
        InOrder inOrder = inOrder(cartItemRepository, foodItemRepository);
        inOrder.verify(cartItemRepository).deleteByFoodItemId(1L);
        inOrder.verify(foodItemRepository).delete(existing);
    }

    @Test
    void deleteFoodItem_releasesRestaurantFromCartsEmptiedByThePurge() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(false);

        foodItemService.deleteFoodItem(1L);

        // The purge bypasses CartService.saveAndConvert, so rule 4 has to be reapplied here or a
        // cart whose only line held this item keeps restaurant_id set and locks its owner out.
        // Ordering matters: the lines must be gone before "which carts are empty" is decided.
        InOrder inOrder = inOrder(cartItemRepository, cartRepository, foodItemRepository);
        inOrder.verify(cartItemRepository).deleteByFoodItemId(1L);
        inOrder.verify(cartRepository).clearRestaurantFromEmptyCarts();
        inOrder.verify(foodItemRepository).delete(existing);
    }

    @Test
    void deleteFoodItem_leavesCartLinesAlone_whenSoftDeleting() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(true);

        foodItemService.deleteFoodItem(1L);

        // The row still exists, so carts holding it stay valid - the line simply reports
        // isAvailable false and drops out of the cart total.
        assertThat(existing.isAvailable()).isFalse();
        verify(cartItemRepository, never()).deleteByFoodItemId(any());
        // No line left a cart, so no cart can have been emptied - nothing to release.
        verify(cartRepository, never()).clearRestaurantFromEmptyCarts();
    }

    @Test
    void deleteFoodItem_throwsFoodItemNotFoundException_whenMissing() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.deleteFoodItem(99L))
                .isInstanceOf(FoodItemNotFoundException.class);
    }

    @Test
    void toggleAvailability_flipsFlagFromTrueToFalse() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemDTO result = foodItemService.toggleAvailability(1L);

        assertThat(result.isAvailable()).isFalse();
        assertThat(existing.isAvailable()).isFalse();
    }

    @Test
    void toggleAvailability_flipsFlagFromFalseToTrue() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        existing.setAvailable(false);
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemDTO result = foodItemService.toggleAvailability(1L);

        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    void toggleAvailability_throwsFoodItemNotFoundException_whenMissing() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.toggleAvailability(99L))
                .isInstanceOf(FoodItemNotFoundException.class);
    }
}
