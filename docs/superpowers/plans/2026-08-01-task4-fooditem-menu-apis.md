# Task 4: Food Item and Menu Management APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Task 4 from `.taskmaster/tasks/tasks.json` — the public restaurant menu API (items grouped by category, optionally filtered to one category, excluding unavailable items), a public single-food-item lookup, and admin CRUD for food items with a soft-delete guard for items that already appear in order history.

**Architecture:** Same layered style as Tasks 1–3: `controller -> service -> repository`, manual DTO mapping via static `from(...)` factory methods, Bean Validation on request DTOs, and exception translation through the existing `GlobalExceptionHandler` (`@RestControllerAdvice`). Public reads live under `/api/restaurants/{id}/menu` (already permit-all via the existing `/api/restaurants/**` matcher) and `/api/food-items/{id}` (newly permit-all). Admin writes live under `/api/admin/food-items/**`, already gated to `ROLE_ADMIN` by `SecurityConfig`'s `/api/admin/**` matcher — no `@PreAuthorize`/method security is introduced.

**Tech Stack:** Spring Boot 4.1.0, Java 21, Spring Data JPA (derived query methods only — no custom `@Query`), Bean Validation (`jakarta.validation`), Lombok on entities, JUnit 5 + Mockito + AssertJ for unit tests, `@WebMvcTest` (from `org.springframework.boot.webmvc.test.autoconfigure`) for controller tests.

## Global Constraints

- Reuse existing entities as-is — **do not modify** `FoodItem.java`, `Restaurant.java`, `Category.java`, `Order.java`, `OrderItem.java`, `OrderStatus.java`. They were validated against the Flyway schema in Task 1.
- No repository-level DB tests: this codebase has zero `@DataJpaTest`/H2 usage and no H2 dependency. Follow the existing convention — cover behavior via service-layer unit tests with mocked repositories, and validate derived-query *names* through the real Spring context (see the `BackendApplicationTests` step in Task 6).
- No `@PreAuthorize`/`@EnableMethodSecurity` — access control stays entirely in `SecurityConfig`'s `requestMatchers(...)` chain, matching Tasks 1–3.
- **Lombok accessor names on `FoodItem.isAvailable`** (exact, verified against the entity source): getter is `isAvailable()`, setter is **`setAvailable(boolean)`** (Lombok strips the `is` prefix for setters on boolean fields already named `isX`), and the builder method is `isAvailable(boolean)`. Use exactly these — do not guess `setIsAvailable`.
- **`FoodItem` uses JPA FIELD access** (`@Id` is on the field), so the JPA metamodel attribute name is `isAvailable`, and the derived query fragment is `...AndIsAvailable`. This is validated for real by the `BackendApplicationTests` step in Task 6, which fails at context startup if a derived query name cannot be resolved.
- `FoodItem.category` and `FoodItem.restaurant` are `FetchType.LAZY`. Any service method that reads `getCategory().getName()` **must** be `@Transactional(readOnly = true)` or it will throw `LazyInitializationException` at runtime. Unit tests with mocked repositories will not catch this — the annotation is mandatory, not optional.
- Public menu response shape (exact, from the task spec): `[{"categoryName": "Appetizers", "items": [{"id", "name", "description", "price", "imageUrl", "isAvailable"}]}]`.
- Public menu excludes unavailable items (`is_available = false`) entirely — filtering happens in the repository query, not in the controller.
- Deterministic ordering: category groups sorted alphabetically by `categoryName`; items within a group sorted ascending by `id`. This matches the `Sort.by("id")` determinism convention introduced in Task 3.
- Price validation: `@NotNull`, strictly greater than 0, at most 8 integer digits and 2 decimal places (the `food_items.price` column is `DECIMAL(10,2)`).
- Name validation: `@NotBlank`, max 150 chars (`food_items.name` is `VARCHAR(150)`). Image URL: optional, max 500 chars (`VARCHAR(500)`), must start with `http://` or `https://` when non-empty (empty string allowed, matching `RestaurantRequest`'s existing convention).
- Admin delete: soft-delete (set `isAvailable = false`) when the item appears in any `order_items` row; hard-delete otherwise.

### Deliberate deviations from the task spec

Two items in the spec's implementation notes are adjusted. Both are called out here so a reviewer can accept or reject them explicitly.

1. **`findByRestaurantId(Long)` and `findByRestaurantIdAndCategoryId(Long, Long)` are NOT added to `FoodItemRepository`.** The spec lists them, but its own test strategy also requires the public menu to exclude unavailable items — which means every menu read must be availability-filtered. Adding the two unfiltered methods would leave dead code with no caller in this task (YAGNI). Instead this plan adds `findByRestaurantIdAndIsAvailable` (spec-named) and `findByRestaurantIdAndCategoryIdAndIsAvailable` (the category-filtered counterpart the spec's `getMenuByRestaurant(restaurantId, categoryId)` signature requires). A future admin-panel task that needs to list unavailable items can add the unfiltered variants then.
2. **`MenuResponse` is the per-category group record, returned as a `List<MenuResponse>`.** The spec names both `MenuResponse` and a "nested structure" but pins the wire format to a JSON *array* of `{categoryName, items}` objects. A single record returned as a list produces exactly that shape without an extra wrapper type.

### Known limitation (accepted, not fixed in this task)

`cart_items.food_item_id` is a foreign key to `food_items`. Hard-deleting a food item that currently sits in someone's cart will raise `DataIntegrityViolationException`, which the existing `GlobalExceptionHandler` already translates to **409 Conflict** with the message "Cannot complete this operation because related records still exist". That is honest, non-crashing behavior. Cleaning up orphaned cart rows belongs to Task 5 (cart), which owns that table — do not add cart handling here.

---

## Baseline

Before starting, confirm the current state (verified on 2026-08-01, commit `e008859`, branch `master`, clean tree):

- `./mvnw test` from `backend/` runs **64 tests**. 63 pass; `BackendApplicationTests.contextLoads` **fails** unless the DB credentials in `../.env` are exported, because `application-dev.yml` resolves `${DB_USERNAME}`/`${DB_PASSWORD}` from the environment. This is pre-existing and not caused by this task.
- With the env exported, all 64 pass. Use this form whenever a step says "with env":

```bash
cd backend && set -a && . ../.env && set +a && ./mvnw test
```

Targeted runs (`-Dtest=SomeTest`) that touch no `@SpringBootTest` class do not need the env.

---

## File Structure

**Backend — new files:**
- `dto/FoodItemDTO.java` — the public item shape, reused for admin responses
- `dto/MenuResponse.java` — one category group: `{categoryName, items}`
- `dto/FoodItemRequest.java` — admin create/update payload with Bean Validation
- `exception/FoodItemNotFoundException.java`
- `exception/CategoryNotFoundException.java`
- `repository/OrderItemRepository.java` — `existsByFoodItemId`, for the soft-delete decision
- `service/FoodItemService.java` — public reads first, admin writes appended in Task 4
- `controller/FoodItemController.java` — public menu + item lookup
- `controller/FoodItemAdminController.java` — admin CRUD

**Backend — modified files:**
- `repository/FoodItemRepository.java` — add two availability-filtered finders
- `exception/GlobalExceptionHandler.java` — 404 handlers for the two new exceptions
- `security/SecurityConfig.java` — add `/api/food-items/**` to the permit-all matcher
- `src/test/.../security/ProbeController.java` — **remove** the `/api/restaurants/{id}/menu` probe (see Task 3, Step 5 — it would collide with the real endpoint), add `/api/food-items/probe`
- `src/test/.../security/SecurityConfigTest.java` — drop the menu-probe assertion, add a food-items assertion
- `src/test/.../exception/GlobalExceptionHandlerTest.java` — two new test methods

**Backend — new test files:**
- `service/FoodItemServiceTest.java`
- `controller/FoodItemControllerTest.java`
- `controller/FoodItemAdminControllerTest.java`

---

### Task 1: DTOs, repository finders, exceptions

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/dto/FoodItemDTO.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/MenuResponse.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/FoodItemRequest.java`
- Create: `backend/src/main/java/com/foodrush/backend/exception/FoodItemNotFoundException.java`
- Create: `backend/src/main/java/com/foodrush/backend/exception/CategoryNotFoundException.java`
- Create: `backend/src/main/java/com/foodrush/backend/repository/OrderItemRepository.java`
- Modify: `backend/src/main/java/com/foodrush/backend/repository/FoodItemRepository.java`

**Interfaces:**
- Produces: `FoodItemDTO(Long id, String name, String description, BigDecimal price, String imageUrl, boolean isAvailable)` with static `FoodItemDTO.from(FoodItem)`; `MenuResponse(String categoryName, List<FoodItemDTO> items)`; `FoodItemRequest(Long restaurantId, Long categoryId, String name, String description, BigDecimal price, String imageUrl)`; `FoodItemNotFoundException extends RuntimeException`; `CategoryNotFoundException extends RuntimeException`; `OrderItemRepository.existsByFoodItemId(Long): boolean`; `FoodItemRepository.findByRestaurantIdAndIsAvailable(Long, boolean): List<FoodItem>` and `FoodItemRepository.findByRestaurantIdAndCategoryIdAndIsAvailable(Long, Long, boolean): List<FoodItem>`.
- These are pure data classes and interfaces with no branching logic of their own, so this task has no dedicated unit test — they are exercised by `FoodItemServiceTest` (Task 2) and the validation cases in `FoodItemAdminControllerTest` (Task 5). Verify here by compiling.

- [ ] **Step 1: Create `FoodItemDTO`**

Create `backend/src/main/java/com/foodrush/backend/dto/FoodItemDTO.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.FoodItem;

import java.math.BigDecimal;

public record FoodItemDTO(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean isAvailable
) {

    public static FoodItemDTO from(FoodItem foodItem) {
        return new FoodItemDTO(
                foodItem.getId(),
                foodItem.getName(),
                foodItem.getDescription(),
                foodItem.getPrice(),
                foodItem.getImageUrl(),
                foodItem.isAvailable());
    }
}
```

- [ ] **Step 2: Create `MenuResponse`**

Create `backend/src/main/java/com/foodrush/backend/dto/MenuResponse.java`:

```java
package com.foodrush.backend.dto;

import java.util.List;

public record MenuResponse(String categoryName, List<FoodItemDTO> items) {
}
```

- [ ] **Step 3: Create `FoodItemRequest`**

Create `backend/src/main/java/com/foodrush/backend/dto/FoodItemRequest.java`:

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodItemRequest(
        @NotNull(message = "Restaurant ID is required")
        Long restaurantId,

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
        BigDecimal price,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        @Pattern(regexp = "^$|^https?://.+", message = "Image URL must be a valid http(s) URL")
        String imageUrl
) {
}
```

- [ ] **Step 4: Create the two exceptions**

Create `backend/src/main/java/com/foodrush/backend/exception/FoodItemNotFoundException.java`:

```java
package com.foodrush.backend.exception;

public class FoodItemNotFoundException extends RuntimeException {

    public FoodItemNotFoundException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/com/foodrush/backend/exception/CategoryNotFoundException.java`:

```java
package com.foodrush.backend.exception;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Create `OrderItemRepository`**

Create `backend/src/main/java/com/foodrush/backend/repository/OrderItemRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByFoodItemId(Long foodItemId);
}
```

- [ ] **Step 6: Add the two finders to `FoodItemRepository`**

Replace the whole of `backend/src/main/java/com/foodrush/backend/repository/FoodItemRepository.java` with:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    long deleteByRestaurantId(Long restaurantId);

    List<FoodItem> findByRestaurantIdAndIsAvailable(Long restaurantId, boolean isAvailable);

    List<FoodItem> findByRestaurantIdAndCategoryIdAndIsAvailable(Long restaurantId, Long categoryId, boolean isAvailable);
}
```

- [ ] **Step 7: Verify it compiles**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS (no output on success with `-q`).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/dto/FoodItemDTO.java \
        backend/src/main/java/com/foodrush/backend/dto/MenuResponse.java \
        backend/src/main/java/com/foodrush/backend/dto/FoodItemRequest.java \
        backend/src/main/java/com/foodrush/backend/exception/FoodItemNotFoundException.java \
        backend/src/main/java/com/foodrush/backend/exception/CategoryNotFoundException.java \
        backend/src/main/java/com/foodrush/backend/repository/OrderItemRepository.java \
        backend/src/main/java/com/foodrush/backend/repository/FoodItemRepository.java
git commit -m "feat: add food item DTOs, exceptions and menu repository finders"
```

---

### Task 2: `FoodItemService` — public menu reads + 404 handlers

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/service/FoodItemService.java` (public reads only — admin methods added in Task 4)
- Modify: `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java`
- Test: `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java` (extend existing file — do not replace it)

**Interfaces:**
- Consumes: `FoodItemDTO.from`, `MenuResponse`, `FoodItemNotFoundException`, `CategoryNotFoundException`, `OrderItemRepository`, the new `FoodItemRepository` finders (all Task 1); existing `RestaurantRepository`, `CategoryRepository`, `RestaurantNotFoundException`.
- Produces: `FoodItemService.getMenuByRestaurant(Long restaurantId, Long categoryId): List<MenuResponse>` (throws `RestaurantNotFoundException`), `FoodItemService.getFoodItemById(Long id): FoodItemDTO` (throws `FoodItemNotFoundException`). Constructor: `FoodItemService(FoodItemRepository, RestaurantRepository, CategoryRepository, OrderItemRepository)` — all four are wired now even though `CategoryRepository`/`OrderItemRepository` are only used by the admin methods in Task 4, so the constructor shape does not change again.
- Also produces on `GlobalExceptionHandler`: `handleFoodItemNotFound(FoodItemNotFoundException, HttpServletRequest)` → 404, `handleCategoryNotFound(CategoryNotFoundException, HttpServletRequest)` → 404.

- [ ] **Step 1: Write the failing service test**

Create `backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FoodItemServiceTest`
Expected: BUILD FAILURE — compilation error, `FoodItemService` symbol not found.

- [ ] **Step 3: Create `FoodItemService` with the public read methods**

Create `backend/src/main/java/com/foodrush/backend/service/FoodItemService.java`:

```java
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
```

> `categoryRepository` and `orderItemRepository` are unused until Task 4. That is intentional — the constructor is wired once, so `FoodItemServiceTest`'s `setUp` never has to change. Javac does not warn on unused private fields assigned in a constructor, so this compiles cleanly.

- [ ] **Step 4: Run the service tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=FoodItemServiceTest`
Expected: BUILD SUCCESS, 7 tests passed.

- [ ] **Step 5: Add the two failing exception-handler tests**

Add these two methods inside the existing class in `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java`, right after `handleRestaurantHasActiveOrders_returns409WithMessage`. No new imports are needed — both exceptions are in the same package as the test.

```java
    @Test
    void handleFoodItemNotFound_returns404WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/food-items/99");
        FoodItemNotFoundException ex = new FoodItemNotFoundException("Food item not found: 99");

        ResponseEntity<ErrorResponse> response = handler.handleFoodItemNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Food item not found: 99");
    }

    @Test
    void handleCategoryNotFound_returns404WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/admin/food-items");
        CategoryNotFoundException ex = new CategoryNotFoundException("Category not found: 77");

        ResponseEntity<ErrorResponse> response = handler.handleCategoryNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Category not found: 77");
    }
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD FAILURE — compilation error, `handleFoodItemNotFound` / `handleCategoryNotFound` not found on `GlobalExceptionHandler`.

- [ ] **Step 7: Add the two handlers**

In `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`, add these two methods immediately after `handleRestaurantHasActiveOrders` (no imports needed — same package):

```java
    @ExceptionHandler(FoodItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFoodItemNotFound(FoodItemNotFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(CategoryNotFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD SUCCESS, 9 tests passed (7 pre-existing + 2 new).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/FoodItemService.java \
        backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java \
        backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java \
        backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: add menu retrieval service grouped by category with 404 handling"
```

---

### Task 3: `FoodItemController` — public menu endpoints

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/controller/FoodItemController.java`
- Modify: `backend/src/main/java/com/foodrush/backend/security/SecurityConfig.java:52`
- Modify: `backend/src/test/java/com/foodrush/backend/security/ProbeController.java`
- Modify: `backend/src/test/java/com/foodrush/backend/security/SecurityConfigTest.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/FoodItemControllerTest.java`

**Interfaces:**
- Consumes: `FoodItemService.getMenuByRestaurant(Long, Long)`, `FoodItemService.getFoodItemById(Long)` (Task 2); `MenuResponse`, `FoodItemDTO` (Task 1).
- Produces: `GET /api/restaurants/{restaurantId}/menu?category={categoryId}` → `200 List<MenuResponse>` / `404`; `GET /api/food-items/{id}` → `200 FoodItemDTO` / `404`. `/api/food-items/**` becomes publicly readable.

> **Critical, do not skip — mapping collision.** `src/test/.../security/ProbeController.java` currently declares `@GetMapping("/api/restaurants/{id}/menu")`. It is a `@RestController` in the `com.foodrush.backend` package tree, so `BackendApplicationTests` (`@SpringBootTest`, which component-scans the test classpath) registers it alongside the real controllers. Adding the identical mapping to `FoodItemController` makes the context fail to start with `IllegalStateException: Ambiguous mapping`. Step 5 removes the probe; do it in the same commit.

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/com/foodrush/backend/controller/FoodItemControllerTest.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.MenuResponse;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.service.FoodItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FoodItemController.class)
@AutoConfigureMockMvc(addFilters = false)
class FoodItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodItemService foodItemService;

    private FoodItemDTO samosa() {
        return new FoodItemDTO(1L, "Samosa", "Crispy potato pastry", new BigDecimal("60.00"),
                "https://example.com/1.jpg", true);
    }

    @Test
    void getMenu_returns200WithItemsGroupedByCategory() throws Exception {
        when(foodItemService.getMenuByRestaurant(1L, null)).thenReturn(List.of(
                new MenuResponse("Appetizers", List.of(samosa())),
                new MenuResponse("Desserts", List.of(new FoodItemDTO(3L, "Gulab Jamun", "Syrup soaked",
                        new BigDecimal("80.00"), "https://example.com/3.jpg", true)))));

        mockMvc.perform(get("/api/restaurants/1/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Appetizers"))
                .andExpect(jsonPath("$[0].items[0].name").value("Samosa"))
                .andExpect(jsonPath("$[0].items[0].price").value(60.00))
                .andExpect(jsonPath("$[0].items[0].isAvailable").value(true))
                .andExpect(jsonPath("$[1].categoryName").value("Desserts"));
    }

    @Test
    void getMenu_passesCategoryIdToService_whenCategoryParamPresent() throws Exception {
        when(foodItemService.getMenuByRestaurant(1L, 10L))
                .thenReturn(List.of(new MenuResponse("Appetizers", List.of(samosa()))));

        mockMvc.perform(get("/api/restaurants/1/menu").param("category", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Appetizers"));

        verify(foodItemService).getMenuByRestaurant(1L, 10L);
    }

    @Test
    void getMenu_returnsEmptyArray_whenRestaurantHasNoItems() throws Exception {
        when(foodItemService.getMenuByRestaurant(1L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/restaurants/1/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getMenu_returns404_whenRestaurantMissing() throws Exception {
        when(foodItemService.getMenuByRestaurant(eqOrNull(99L), isNull()))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/restaurants/99/menu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFoodItemById_returns200WithItem() throws Exception {
        when(foodItemService.getFoodItemById(1L)).thenReturn(samosa());

        mockMvc.perform(get("/api/food-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Samosa"))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void getFoodItemById_returns404_whenMissing() throws Exception {
        when(foodItemService.getFoodItemById(99L))
                .thenThrow(new FoodItemNotFoundException("Food item not found: 99"));

        mockMvc.perform(get("/api/food-items/99"))
                .andExpect(status().isNotFound());
    }

    private static Long eqOrNull(Long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
```

> The `eqOrNull` helper exists only so the `getMenu_returns404` stub can mix an `eq(...)` matcher with `isNull()` — Mockito requires all-or-nothing matchers in one stub, and a bare `null` literal would be a raw value. No `@Import(GlobalExceptionHandler.class)` is needed: `@WebMvcTest` auto-detects `@RestControllerAdvice` beans, and an explicit import would register it twice and fail context startup.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FoodItemControllerTest`
Expected: BUILD FAILURE — compilation error, `FoodItemController` symbol not found.

- [ ] **Step 3: Create `FoodItemController`**

Create `backend/src/main/java/com/foodrush/backend/controller/FoodItemController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.MenuResponse;
import com.foodrush.backend.service.FoodItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FoodItemController {

    private final FoodItemService foodItemService;

    public FoodItemController(FoodItemService foodItemService) {
        this.foodItemService = foodItemService;
    }

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuResponse>> getMenu(
            @PathVariable Long restaurantId,
            @RequestParam(name = "category", required = false) Long categoryId) {
        return ResponseEntity.ok(foodItemService.getMenuByRestaurant(restaurantId, categoryId));
    }

    @GetMapping("/api/food-items/{id}")
    public ResponseEntity<FoodItemDTO> getFoodItemById(@PathVariable Long id) {
        return ResponseEntity.ok(foodItemService.getFoodItemById(id));
    }
}
```

> This controller has no class-level `@RequestMapping` because its two routes live under different prefixes (`/api/restaurants/...` and `/api/food-items/...`). That is deliberate — both belong to the food-item domain, and Spring maps by annotation, not by class location.

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=FoodItemControllerTest`
Expected: BUILD SUCCESS, 6 tests passed.

- [ ] **Step 5: Remove the colliding menu probe and make `/api/food-items/**` public**

In `backend/src/test/java/com/foodrush/backend/security/ProbeController.java`, **delete** this method:

```java
    @GetMapping("/api/restaurants/{id}/menu")
    String restaurantMenuProbe() {
        return "ok";
    }
```

and **add** this one in its place:

```java
    @GetMapping("/api/food-items/probe")
    String foodItemsProbe() {
        return "ok";
    }
```

In `backend/src/main/java/com/foodrush/backend/security/SecurityConfig.java`, change line 52 from:

```java
                        .requestMatchers("/api/restaurants/**", "/api/categories/**").permitAll()
```

to:

```java
                        .requestMatchers("/api/restaurants/**", "/api/categories/**", "/api/food-items/**").permitAll()
```

In `backend/src/test/java/com/foodrush/backend/security/SecurityConfigTest.java`, replace the `restaurantBrowsing_isPublic` method with these two:

```java
    @Test
    void restaurantBrowsing_isPublic() throws Exception {
        mockMvc.perform(get("/api/restaurants/probe")).andExpect(status().isOk());
    }

    @Test
    void foodItemsEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/food-items/probe")).andExpect(status().isOk());
    }
```

> Dropping the `/api/restaurants/1/menu` assertion loses nothing: the `/api/restaurants/probe` assertion already proves the whole `/api/restaurants/**` matcher is permit-all, and the menu path sits under it. Keeping a probe at that exact path is what breaks the real application context.

- [ ] **Step 6: Run the security tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=SecurityConfigTest`
Expected: BUILD SUCCESS, 10 tests passed (9 pre-existing with `restaurantBrowsing_isPublic` reduced to one assertion, plus the new `foodItemsEndpoint_isPublic`).

- [ ] **Step 7: Verify the real application context still starts (catches ambiguous mappings and bad derived queries)**

Run: `cd backend && set -a && . ../.env && set +a && ./mvnw test -Dtest=BackendApplicationTests`
Expected: BUILD SUCCESS, 1 test passed.
If this fails with `Ambiguous mapping`, Step 5's probe deletion was missed. If it fails with `PropertyReferenceException: No property 'isAvailable' found`, the derived query names in Task 1 Step 6 are wrong for this entity's access type — report it rather than guessing.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/controller/FoodItemController.java \
        backend/src/main/java/com/foodrush/backend/security/SecurityConfig.java \
        backend/src/test/java/com/foodrush/backend/security/ProbeController.java \
        backend/src/test/java/com/foodrush/backend/security/SecurityConfigTest.java \
        backend/src/test/java/com/foodrush/backend/controller/FoodItemControllerTest.java
git commit -m "feat: add public menu and food item endpoints"
```

---

### Task 4: `FoodItemService` — admin write methods

**Files:**
- Modify: `backend/src/main/java/com/foodrush/backend/service/FoodItemService.java`
- Modify: `backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java`

**Interfaces:**
- Consumes: `FoodItemRequest` (Task 1), `CategoryNotFoundException` (Task 1), `OrderItemRepository.existsByFoodItemId` (Task 1), existing `RestaurantRepository`/`CategoryRepository`.
- Produces: `FoodItemService.createFoodItem(FoodItemRequest): FoodItemDTO` (throws `RestaurantNotFoundException`, `CategoryNotFoundException`), `updateFoodItem(Long, FoodItemRequest): FoodItemDTO` (throws `FoodItemNotFoundException`, `RestaurantNotFoundException`, `CategoryNotFoundException`), `deleteFoodItem(Long): void` (throws `FoodItemNotFoundException`), `toggleAvailability(Long): FoodItemDTO` (throws `FoodItemNotFoundException`).

- [ ] **Step 1: Add the failing admin tests to `FoodItemServiceTest`**

Add these imports to the existing `FoodItemServiceTest`:

```java
import com.foodrush.backend.dto.FoodItemRequest;
import com.foodrush.backend.exception.CategoryNotFoundException;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.never;
```

Add these methods to the existing class:

```java
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
        when(foodItemRepository.save(org.mockito.ArgumentMatchers.any(FoodItem.class)))
                .thenAnswer(invocation -> {
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

        verify(foodItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
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

        verify(foodItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
        when(foodItemRepository.save(org.mockito.ArgumentMatchers.any(FoodItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        verify(foodItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteFoodItem_softDeletesBySettingUnavailable_whenOrderHistoryExists() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(true);

        foodItemService.deleteFoodItem(1L);

        assertThat(existing.isAvailable()).isFalse();
        verify(foodItemRepository).save(existing);
        verify(foodItemRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteFoodItem_hardDeletes_whenNoOrderHistory() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(false);

        foodItemService.deleteFoodItem(1L);

        verify(foodItemRepository).delete(existing);
        verify(foodItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
        when(foodItemRepository.save(org.mockito.ArgumentMatchers.any(FoodItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemDTO result = foodItemService.toggleAvailability(1L);

        assertThat(result.isAvailable()).isFalse();
        assertThat(existing.isAvailable()).isFalse();
    }

    @Test
    void toggleAvailability_flipsFlagFromFalseToTrue() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        existing.setAvailable(false);
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(foodItemRepository.save(org.mockito.ArgumentMatchers.any(FoodItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemDTO result = foodItemService.toggleAvailability(1L);

        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    void toggleAvailability_throwsFoodItemNotFoundException_whenMissing() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodItemService.toggleAvailability(99L))
                .isInstanceOf(FoodItemNotFoundException.class);
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FoodItemServiceTest`
Expected: BUILD FAILURE — compilation error, `createFoodItem`/`updateFoodItem`/`deleteFoodItem`/`toggleAvailability` not found.

- [ ] **Step 3: Add the admin methods to `FoodItemService`**

In `backend/src/main/java/com/foodrush/backend/service/FoodItemService.java`, add these imports:

```java
import com.foodrush.backend.dto.FoodItemRequest;
import com.foodrush.backend.entity.Category;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.CategoryNotFoundException;
```

Add these methods at the end of the class, just above the existing private `requireFoodItem` helper:

```java
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
        // isAvailable is deliberately untouched — it is owned by toggleAvailability/deleteFoodItem.
        return FoodItemDTO.from(foodItemRepository.save(foodItem));
    }

    /**
     * Soft-deletes (marks unavailable) any item that already appears in order history, so past
     * orders keep referring to a real row. Items with no order history are removed outright.
     */
    @Transactional
    public void deleteFoodItem(Long id) {
        FoodItem foodItem = requireFoodItem(id);
        if (orderItemRepository.existsByFoodItemId(id)) {
            foodItem.setAvailable(false);
            foodItemRepository.save(foodItem);
            return;
        }
        foodItemRepository.delete(foodItem);
    }

    @Transactional
    public FoodItemDTO toggleAvailability(Long id) {
        FoodItem foodItem = requireFoodItem(id);
        foodItem.setAvailable(!foodItem.isAvailable());
        return FoodItemDTO.from(foodItemRepository.save(foodItem));
    }

    private Restaurant requireRestaurant(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));
    }
```

- [ ] **Step 4: Run the service tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=FoodItemServiceTest`
Expected: BUILD SUCCESS, 18 tests passed (7 from Task 2 + 11 new).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/FoodItemService.java \
        backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java
git commit -m "feat: add admin food item create/update/delete with order-history soft delete"
```

---

### Task 5: `FoodItemAdminController` — admin endpoints

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/controller/FoodItemAdminController.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/FoodItemAdminControllerTest.java`

**Interfaces:**
- Consumes: `FoodItemService.createFoodItem/updateFoodItem/deleteFoodItem/toggleAvailability` (Task 4), `FoodItemRequest`, `FoodItemDTO` (Task 1).
- Produces: `POST /api/admin/food-items` → `201 FoodItemDTO`; `PUT /api/admin/food-items/{id}` → `200 FoodItemDTO`; `DELETE /api/admin/food-items/{id}` → `204`; `PATCH /api/admin/food-items/{id}/availability` → `200 FoodItemDTO`. All are already `ROLE_ADMIN`-gated by the existing `/api/admin/**` matcher.

- [ ] **Step 1: Write the failing admin controller test**

Create `backend/src/test/java/com/foodrush/backend/controller/FoodItemAdminControllerTest.java`:

```java
package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.FoodItemRequest;
import com.foodrush.backend.exception.CategoryNotFoundException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.service.FoodItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoodItemAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class FoodItemAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FoodItemService foodItemService;

    private FoodItemDTO sampleDTO() {
        return new FoodItemDTO(1L, "Samosa", "Crispy potato pastry", new BigDecimal("60.00"),
                "https://example.com/1.jpg", true);
    }

    private FoodItemRequest validRequest() {
        return new FoodItemRequest(1L, 10L, "Samosa", "Crispy potato pastry",
                new BigDecimal("60.00"), "https://example.com/1.jpg");
    }

    @Test
    void createFoodItem_returns201WithCreatedItem() throws Exception {
        when(foodItemService.createFoodItem(any(FoodItemRequest.class))).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Samosa"))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void createFoodItem_returns400_whenPriceIsNegative() throws Exception {
        FoodItemRequest request = new FoodItemRequest(1L, 10L, "Samosa", null,
                new BigDecimal("-1.00"), null);

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFoodItem_returns400_whenPriceIsZero() throws Exception {
        FoodItemRequest request = new FoodItemRequest(1L, 10L, "Samosa", null,
                new BigDecimal("0.00"), null);

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFoodItem_returns400_whenNameBlank() throws Exception {
        FoodItemRequest request = new FoodItemRequest(1L, 10L, "  ", null,
                new BigDecimal("60.00"), null);

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFoodItem_returns400_whenRestaurantIdMissing() throws Exception {
        FoodItemRequest request = new FoodItemRequest(null, 10L, "Samosa", null,
                new BigDecimal("60.00"), null);

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFoodItem_returns404_whenRestaurantDoesNotExist() throws Exception {
        FoodItemRequest request = new FoodItemRequest(99L, 10L, "Samosa", null,
                new BigDecimal("60.00"), null);
        when(foodItemService.createFoodItem(any(FoodItemRequest.class)))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createFoodItem_returns404_whenCategoryDoesNotExist() throws Exception {
        FoodItemRequest request = new FoodItemRequest(1L, 77L, "Samosa", null,
                new BigDecimal("60.00"), null);
        when(foodItemService.createFoodItem(any(FoodItemRequest.class)))
                .thenThrow(new CategoryNotFoundException("Category not found: 77"));

        mockMvc.perform(post("/api/admin/food-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFoodItem_returns200WithUpdatedItem() throws Exception {
        when(foodItemService.updateFoodItem(eq(1L), any(FoodItemRequest.class))).thenReturn(sampleDTO());

        mockMvc.perform(put("/api/admin/food-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Samosa"));
    }

    @Test
    void updateFoodItem_returns400_whenPriceIsNegative() throws Exception {
        FoodItemRequest request = new FoodItemRequest(1L, 10L, "Samosa", null,
                new BigDecimal("-5.00"), null);

        mockMvc.perform(put("/api/admin/food-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFoodItem_returns404_whenFoodItemMissing() throws Exception {
        when(foodItemService.updateFoodItem(eq(99L), any(FoodItemRequest.class)))
                .thenThrow(new FoodItemNotFoundException("Food item not found: 99"));

        mockMvc.perform(put("/api/admin/food-items/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFoodItem_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/food-items/1"))
                .andExpect(status().isNoContent());

        verify(foodItemService).deleteFoodItem(1L);
    }

    @Test
    void deleteFoodItem_returns404_whenMissing() throws Exception {
        doThrow(new FoodItemNotFoundException("Food item not found: 99"))
                .when(foodItemService).deleteFoodItem(99L);

        mockMvc.perform(delete("/api/admin/food-items/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleAvailability_returns200WithUpdatedItem() throws Exception {
        when(foodItemService.toggleAvailability(1L)).thenReturn(
                new FoodItemDTO(1L, "Samosa", "Crispy potato pastry", new BigDecimal("60.00"),
                        "https://example.com/1.jpg", false));

        mockMvc.perform(patch("/api/admin/food-items/1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    void toggleAvailability_returns404_whenMissing() throws Exception {
        when(foodItemService.toggleAvailability(99L))
                .thenThrow(new FoodItemNotFoundException("Food item not found: 99"));

        mockMvc.perform(patch("/api/admin/food-items/99/availability"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FoodItemAdminControllerTest`
Expected: BUILD FAILURE — compilation error, `FoodItemAdminController` symbol not found.

- [ ] **Step 3: Create `FoodItemAdminController`**

Create `backend/src/main/java/com/foodrush/backend/controller/FoodItemAdminController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.FoodItemDTO;
import com.foodrush.backend.dto.FoodItemRequest;
import com.foodrush.backend.service.FoodItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/food-items")
public class FoodItemAdminController {

    private final FoodItemService foodItemService;

    public FoodItemAdminController(FoodItemService foodItemService) {
        this.foodItemService = foodItemService;
    }

    @PostMapping
    public ResponseEntity<FoodItemDTO> createFoodItem(@Valid @RequestBody FoodItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodItemService.createFoodItem(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FoodItemDTO> updateFoodItem(@PathVariable Long id,
                                                       @Valid @RequestBody FoodItemRequest request) {
        return ResponseEntity.ok(foodItemService.updateFoodItem(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFoodItem(@PathVariable Long id) {
        foodItemService.deleteFoodItem(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<FoodItemDTO> toggleAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(foodItemService.toggleAvailability(id));
    }
}
```

- [ ] **Step 4: Run the admin controller tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=FoodItemAdminControllerTest`
Expected: BUILD SUCCESS, 14 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/controller/FoodItemAdminController.java \
        backend/src/test/java/com/foodrush/backend/controller/FoodItemAdminControllerTest.java
git commit -m "feat: add admin food item management endpoints"
```

---

### Task 6: Full-suite verification and Task Master status

**Files:**
- No source changes. This task is verification only.

**Interfaces:**
- Consumes: everything from Tasks 1–5.
- Produces: a green suite and Task 4 marked `done` in `.taskmaster/tasks/tasks.json` (via the CLI — never hand-edit that file).

- [ ] **Step 1: Run the whole suite against the real database**

Run: `cd backend && set -a && . ../.env && set +a && ./mvnw test`
Expected: BUILD SUCCESS. Test count should be **64 (baseline) + 44 new = 108**, 0 failures, 0 errors.
New tests: 7 (`FoodItemServiceTest` reads) + 11 (`FoodItemServiceTest` admin) + 2 (`GlobalExceptionHandlerTest`) + 6 (`FoodItemControllerTest`) + 14 (`FoodItemAdminControllerTest`) + 1 (`SecurityConfigTest.foodItemsEndpoint_isPublic`) + 3 net-new from splitting = confirm the actual number from the surefire summary rather than asserting this figure if it differs; what matters is **0 failures and 0 errors**.

- [ ] **Step 2: Confirm no failures remain**

Run: `cd backend && grep -h "tests=" target/surefire-reports/*.xml | grep -oE 'failures="[0-9]+" errors="[0-9]+"' | sort -u`
Expected: only `failures="0" errors="0"` lines.

- [ ] **Step 3: Confirm the working tree is clean and every change is committed**

Run: `git status --short`
Expected: empty output.

- [ ] **Step 4: Mark Task 4 done in Task Master**

Run: `cd .. && npx task-master set-status --id=4 --status=done`
Expected: confirmation that task 4 is now `done`.

- [ ] **Step 5: Commit the Task Master status change**

```bash
git add .taskmaster/tasks/tasks.json
git commit -m "chore(taskmaster): mark Task 4 (food item/menu APIs) as done"
```

---

## Self-Review

**Spec coverage** — every requirement in the task-4 spec maps to a task here:

| Spec requirement | Where |
|---|---|
| `FoodItemRepository` availability-filtered finders | Task 1 Step 6 (with the documented deviation on the two unfiltered finders) |
| `FoodItemService.getMenuByRestaurant(restaurantId, categoryId)` grouped by category | Task 2 Step 3 |
| `FoodItemService.getFoodItemById(id)` | Task 2 Step 3 |
| `GET /api/restaurants/{restaurantId}/menu?category=` grouped response | Task 3 Step 3 |
| Response shape `[{categoryName, items:[{id,name,description,price,imageUrl,isAvailable}]}]` | Task 1 Steps 1–2, asserted in Task 3 Step 1 |
| `FoodItemDTO`, `MenuResponse` | Task 1 Steps 1–2 |
| `POST /api/admin/food-items` with validation, `isAvailable=true` default | Task 4 Step 3, Task 5 Step 3 |
| `PUT /api/admin/food-items/{id}` | Task 4 Step 3, Task 5 Step 3 |
| `DELETE /api/admin/food-items/{id}` soft/hard delete | Task 4 Step 3 |
| `PATCH /api/admin/food-items/{id}/availability` toggle | Task 4 Step 3, Task 5 Step 3 |
| Price positive, name non-empty, restaurant/category FK validation | Task 1 Step 3 (Bean Validation), Task 4 Step 3 (FK existence → 404) |

**Test-strategy coverage** — each bullet in the spec's test strategy has a named test:

| Spec test | Test method |
|---|---|
| Menu grouped by category | `getMenuByRestaurant_groupsItemsByCategoryName_sortedByCategoryThenItemId`, `getMenu_returns200WithItemsGroupedByCategory` |
| Category filter returns only that category | `getMenuByRestaurant_filtersToSingleCategory_whenCategoryIdProvided`, `getMenu_passesCategoryIdToService_whenCategoryParamPresent` |
| Menu excludes unavailable items | `getMenuByRestaurant_requestsOnlyAvailableItems` |
| Admin create with valid restaurant + category | `createFoodItem_savesWithAvailabilityTrue_andReturnsDTO`, `createFoodItem_returns201WithCreatedItem` |
| Admin create fails with non-existent restaurantId (404) | `createFoodItem_throwsRestaurantNotFoundException_whenRestaurantMissing`, `createFoodItem_returns404_whenRestaurantDoesNotExist` |
| Update price validation (negative rejected) | `createFoodItem_returns400_whenPriceIsNegative`, `createFoodItem_returns400_whenPriceIsZero`, `updateFoodItem_returns400_whenPriceIsNegative` |
| Admin delete / toggle availability | `deleteFoodItem_softDeletesBySettingUnavailable_whenOrderHistoryExists`, `deleteFoodItem_hardDeletes_whenNoOrderHistory`, `toggleAvailability_flipsFlagFromTrueToFalse`, `toggleAvailability_flipsFlagFromFalseToTrue` |
| Unavailable items don't appear in public menu | `getMenuByRestaurant_requestsOnlyAvailableItems` (repository call asserted with `true`) |
| Restaurant with no items returns empty array | `getMenuByRestaurant_returnsEmptyList_whenRestaurantHasNoItems`, `getMenu_returnsEmptyArray_whenRestaurantHasNoItems` |

**Type consistency** — checked across tasks: `FoodItemDTO.from`, `MenuResponse(String, List<FoodItemDTO>)`, `FoodItemRequest` component order `(restaurantId, categoryId, name, description, price, imageUrl)` is identical in every constructor call; `FoodItemService`'s four-arg constructor is fixed in Task 2 and never changed in Task 4; `foodItem(...)` test helper signature `(Long, String, Long, String, String)` is used consistently in Tasks 2 and 4; Lombok accessors are `isAvailable()` / `setAvailable(boolean)` / builder `.isAvailable(boolean)` everywhere.
