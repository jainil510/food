# Task 5: Shopping Cart APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give an authenticated user a persistent cart — add items, adjust quantities, remove items, clear it, and read it back with an auto-calculated total — enforcing FR-14, the rule that a cart holds items from exactly one restaurant at a time.

**Architecture:** Service-layer orchestration with anemic JPA entities, matching `RestaurantService` and `FoodItemService`. `CartService` owns every invariant; `Cart`/`CartItem` stay plain entities; `CartDTO`/`CartItemDTO` are records with static `from(...)` factories that also compute subtotals and the total, exactly as `FoodItemDTO.from` does. `CartController` is five thin delegating methods.

**Tech Stack:** Spring Boot 4.1.0, Java 21, Spring Data JPA, Spring Security (JWT, already configured), Bean Validation, JUnit 5 + Mockito + AssertJ, MockMvc.

**Source spec:** `docs/superpowers/specs/2026-08-01-task5-cart-apis-design.md`

## Global Constraints

- Base package is `com.foodrush.backend`. Layers: `controller -> service -> repository -> entity`, plus `dto`/`exception`/`security`.
- Run all commands from the `backend/` directory. Tests: `./mvnw test`. Single test class: `./mvnw test -Dtest=ClassName`.
- **The `.env` values must be exported before running tests.** A bare `./mvnw test` fails `contextLoads` on an unresolved `${DB_USERNAME}`. That failure is environment-only and is not caused by anything in this plan.
- `/api/cart/**` is already `authenticated()` in `SecurityConfig`. **Do not modify `SecurityConfig`.** Unauthenticated access already returns 401 and is already tested by `SecurityConfigTest.cartEndpoint_rejectsUnauthenticatedRequest_with401`.
- The test-only `ProbeController` maps `GET /api/cart/probe`. **Never add a `GET /api/cart/{pathVariable}` route** — it would ambiguously collide with that mapping under `@SpringBootTest` and break unrelated tests.
- `cart_items` has **no** unique constraint on `(cart_id, food_item_id)`. Duplicate prevention is the service's job.
- Entities use Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. DTOs are Java `record`s. Never add Lombok to a DTO.
- All money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Never `double`.
- Exception messages that are user-facing are copied verbatim from this plan. The single-restaurant message is exactly: `Cart can only contain items from one restaurant. Clear cart first?`
- Commit after every task. Never use `--no-verify`.

## Business rules (referenced by number throughout)

1. **Single restaurant** — on add, cart's restaurant must be null or equal the item's; else 409.
2. **Merge, don't duplicate** — adding an item already in the cart increments its quantity.
3. **Quantity 0 removes** — `PUT` with quantity 0 deletes the line.
4. **Emptying resets the restaurant** — when the last line leaves, `cart.restaurant` becomes null.
5. **Unavailable items cannot be added** — 409.
6. **Unavailable lines already in the cart are flagged, not purged** — returned with `isAvailable: false`, excluded from `total`.
7. **Ownership** — a `cartItemId` not in the requesting user's cart is a 404.
8. **Total** — `BigDecimal` sum of available lines' `price × quantity`, scale 2.

**Validation order on add:** item exists (404) → available (409) → restaurant matches (409).

## File structure

| File | Responsibility |
|---|---|
| `dto/CartItemDTO.java` (create) | One cart line + its computed subtotal and availability flag |
| `dto/CartDTO.java` (create) | Whole cart + computed total; `empty()` for the no-cart case |
| `dto/AddToCartRequest.java` (create) | `POST /api/cart/items` body + validation |
| `dto/UpdateCartItemRequest.java` (create) | `PUT /api/cart/items/{id}` body + validation |
| `exception/CartItemNotFoundException.java` (create) | 404 for missing/not-owned lines |
| `exception/CartConflictException.java` (create) | 409 for both conflict messages |
| `exception/GlobalExceptionHandler.java` (modify) | Map the two new exceptions |
| `entity/Cart.java` (modify) | Add `@OrderBy("id")` for deterministic line order |
| `repository/CartRepository.java` (create) | `findByUserId` with an entity graph |
| `repository/CartItemRepository.java` (create) | `deleteByFoodItemId` for the Task 4 fix |
| `service/CartService.java` (create) | All eight business rules |
| `controller/CartController.java` (create) | Five endpoints, principal resolution |
| `service/FoodItemService.java` (modify) | Purge cart lines before a hard delete |

### Deviations from the task description in `tasks.json`, decided in the spec

- `CartItemRepository.findByCartId` is omitted — `Cart.items` already provides it.
- `CartItemRepository.findByCartIdAndFoodItemId` is **also** omitted. The entity graph on `findByUserId` has already loaded the lines, so both the merge lookup (rule 2) and the ownership check (rule 7) scan `cart.getItems()` in memory instead of issuing a second query. This is a refinement of the spec's component list, made for the same reason `findByCartId` was dropped.
- User ID comes from `@AuthenticationPrincipal UserPrincipal`, not `SecurityContextHolder`.
- All five endpoints return the full `CartDTO`; the `DELETE`s return 200 + body, not 204.

---

### Task 1: DTOs and exceptions

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/dto/CartItemDTO.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/CartDTO.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/AddToCartRequest.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/UpdateCartItemRequest.java`
- Create: `backend/src/main/java/com/foodrush/backend/exception/CartItemNotFoundException.java`
- Create: `backend/src/main/java/com/foodrush/backend/exception/CartConflictException.java`
- Modify: `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/foodrush/backend/entity/Cart.java`
- Test: `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java` (modify)
- Test: `backend/src/test/java/com/foodrush/backend/dto/CartDTOTest.java` (create)

**Interfaces:**
- Consumes: `Cart`, `CartItem`, `FoodItem`, `Restaurant` entities; `ErrorResponse` (existing).
- Produces:
  - `CartItemDTO(Long cartItemId, Long foodItemId, String name, BigDecimal price, Integer quantity, BigDecimal subtotal, boolean isAvailable)` with `static CartItemDTO from(CartItem)`
  - `CartDTO(Long restaurantId, String restaurantName, List<CartItemDTO> items, BigDecimal total)` with `static CartDTO from(Cart)` and `static CartDTO empty()`
  - `AddToCartRequest(Long foodItemId, Integer quantity)`
  - `UpdateCartItemRequest(Integer quantity)`
  - `CartItemNotFoundException(String)`, `CartConflictException(String)`
  - `GlobalExceptionHandler.handleCartItemNotFound(...)`, `.handleCartConflict(...)`

- [ ] **Step 1: Write the failing DTO test**

Create `backend/src/test/java/com/foodrush/backend/dto/CartDTOTest.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CartDTOTest {

    private FoodItem foodItem(Long id, String name, String price, boolean available) {
        return FoodItem.builder()
                .id(id)
                .restaurant(Restaurant.builder().id(1L).name("Spice Route").build())
                .name(name)
                .price(new BigDecimal(price))
                .isAvailable(available)
                .build();
    }

    private Cart cartWith(CartItem... items) {
        return Cart.builder()
                .id(100L)
                .restaurant(Restaurant.builder().id(1L).name("Spice Route").build())
                .items(new ArrayList<>(List.of(items)))
                .build();
    }

    private CartItem cartItem(Long id, FoodItem foodItem, int quantity) {
        return CartItem.builder().id(id).foodItem(foodItem).quantity(quantity).build();
    }

    @Test
    void from_computesSubtotalPerLine() {
        Cart cart = cartWith(cartItem(1L, foodItem(10L, "Samosa", "60.00", true), 3));

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).cartItemId()).isEqualTo(1L);
        assertThat(dto.items().get(0).foodItemId()).isEqualTo(10L);
        assertThat(dto.items().get(0).name()).isEqualTo("Samosa");
        assertThat(dto.items().get(0).quantity()).isEqualTo(3);
        assertThat(dto.items().get(0).subtotal()).isEqualByComparingTo("180.00");
        assertThat(dto.items().get(0).isAvailable()).isTrue();
    }

    @Test
    void from_sumsTotalAcrossMultipleLinesAndQuantities() {
        Cart cart = cartWith(
                cartItem(1L, foodItem(10L, "Samosa", "60.00", true), 3),
                cartItem(2L, foodItem(11L, "Paneer Tikka", "240.50", true), 2));

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.total()).isEqualByComparingTo("661.00");
        assertThat(dto.restaurantId()).isEqualTo(1L);
        assertThat(dto.restaurantName()).isEqualTo("Spice Route");
    }

    @Test
    void from_flagsUnavailableLineAndExcludesItFromTotal() {
        Cart cart = cartWith(
                cartItem(1L, foodItem(10L, "Samosa", "60.00", true), 2),
                cartItem(2L, foodItem(11L, "Paneer Tikka", "220.00", false), 1));

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.items()).hasSize(2);
        assertThat(dto.items().get(1).isAvailable()).isFalse();
        // The unavailable line still reports its own subtotal...
        assertThat(dto.items().get(1).subtotal()).isEqualByComparingTo("220.00");
        // ...but does not contribute to the cart total.
        assertThat(dto.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void from_returnsNullRestaurantFields_whenCartHasNoRestaurant() {
        Cart cart = Cart.builder().id(100L).items(new ArrayList<>()).build();

        CartDTO dto = CartDTO.from(cart);

        assertThat(dto.restaurantId()).isNull();
        assertThat(dto.restaurantName()).isNull();
        assertThat(dto.items()).isEmpty();
        assertThat(dto.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void empty_returnsZeroTotalAndNoItems() {
        CartDTO dto = CartDTO.empty();

        assertThat(dto.restaurantId()).isNull();
        assertThat(dto.restaurantName()).isNull();
        assertThat(dto.items()).isEmpty();
        assertThat(dto.total()).isEqualByComparingTo("0.00");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=CartDTOTest`
Expected: BUILD FAILURE — compilation error, `CartDTO` and `CartItemDTO` do not exist.

- [ ] **Step 3: Create `CartItemDTO`**

Create `backend/src/main/java/com/foodrush/backend/dto/CartItemDTO.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CartItemDTO(
        Long cartItemId,
        Long foodItemId,
        String name,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotal,
        boolean isAvailable
) {

    public static CartItemDTO from(CartItem cartItem) {
        FoodItem foodItem = cartItem.getFoodItem();
        BigDecimal subtotal = foodItem.getPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new CartItemDTO(
                cartItem.getId(),
                foodItem.getId(),
                foodItem.getName(),
                foodItem.getPrice(),
                cartItem.getQuantity(),
                subtotal,
                foodItem.isAvailable());
    }
}
```

- [ ] **Step 4: Create `CartDTO`**

Create `backend/src/main/java/com/foodrush/backend/dto/CartDTO.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.Restaurant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record CartDTO(
        Long restaurantId,
        String restaurantName,
        List<CartItemDTO> items,
        BigDecimal total
) {

    /**
     * Lines whose food item has since become unavailable are still returned, flagged, so the
     * user gets an explanation instead of a silently vanishing item - but they are left out
     * of the total, because they cannot be ordered.
     */
    public static CartDTO from(Cart cart) {
        List<CartItemDTO> items = cart.getItems().stream()
                .map(CartItemDTO::from)
                .toList();
        BigDecimal total = items.stream()
                .filter(CartItemDTO::isAvailable)
                .map(CartItemDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Restaurant restaurant = cart.getRestaurant();
        return new CartDTO(
                restaurant == null ? null : restaurant.getId(),
                restaurant == null ? null : restaurant.getName(),
                items,
                total);
    }

    /** Representation for a user who has no cart row yet. Deliberately does not create one. */
    public static CartDTO empty() {
        return new CartDTO(null, null, List.of(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
```

- [ ] **Step 5: Add `@OrderBy` to `Cart.items` for deterministic line order**

In `backend/src/main/java/com/foodrush/backend/entity/Cart.java`, add the import
`jakarta.persistence.OrderBy` and annotate the collection. The field becomes:

```java
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
```

Without this, a `@OneToMany` has no defined order and `CartDTO.items` would come back in an
arbitrary order, making both the API and its tests non-deterministic.

- [ ] **Step 6: Run the DTO test to verify it passes**

Run: `./mvnw test -Dtest=CartDTOTest`
Expected: PASS, 5 tests.

- [ ] **Step 7: Create the two exceptions**

Create `backend/src/main/java/com/foodrush/backend/exception/CartItemNotFoundException.java`:

```java
package com.foodrush.backend.exception;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/com/foodrush/backend/exception/CartConflictException.java`:

```java
package com.foodrush.backend.exception;

/**
 * A cart operation that is valid input but conflicts with cart or catalogue state: adding an
 * item from a second restaurant, or adding an item that is no longer available. Maps to 409.
 */
public class CartConflictException extends RuntimeException {

    public CartConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 8: Write the failing exception-handler tests**

Append these two tests to `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java`, inside the existing class:

```java
    @Test
    void handleCartItemNotFound_returns404WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/cart/items/42");
        CartItemNotFoundException ex = new CartItemNotFoundException("Cart item not found: 42");

        ResponseEntity<ErrorResponse> response = handler.handleCartItemNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Cart item not found: 42");
        assertThat(response.getBody().path()).isEqualTo("/api/cart/items/42");
    }

    @Test
    void handleCartConflict_returns409WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/cart/items");
        CartConflictException ex = new CartConflictException(
                "Cart can only contain items from one restaurant. Clear cart first?");

        ResponseEntity<ErrorResponse> response = handler.handleCartConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Cart can only contain items from one restaurant. Clear cart first?");
    }
```

- [ ] **Step 9: Run to verify it fails**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD FAILURE — compilation error, `handleCartItemNotFound` / `handleCartConflict` do not exist.

- [ ] **Step 10: Register both handlers**

In `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`, add these
two methods immediately after `handleCategoryNotFound` (keep the existing generic
`handleUnexpected` last):

```java
    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartItemNotFound(CartItemNotFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(CartConflictException.class)
    public ResponseEntity<ErrorResponse> handleCartConflict(CartConflictException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }
```

- [ ] **Step 11: Run to verify it passes**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS, all existing tests plus the 2 new ones.

- [ ] **Step 12: Create the two request DTOs**

Create `backend/src/main/java/com/foodrush/backend/dto/AddToCartRequest.java`:

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddToCartRequest(
        @NotNull(message = "Food item ID is required")
        Long foodItemId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
}
```

Create `backend/src/main/java/com/foodrush/backend/dto/UpdateCartItemRequest.java`:

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        /*
         * 0 is allowed here but not on AddToCartRequest: updating to 0 is the documented way
         * to remove a line, while adding zero of something is meaningless.
         */
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be 0 or greater")
        Integer quantity
) {
}
```

- [ ] **Step 13: Compile and commit**

Run: `./mvnw test -Dtest=CartDTOTest+GlobalExceptionHandlerTest`
Expected: PASS.

```bash
git add backend/src/main/java/com/foodrush/backend/dto/CartItemDTO.java \
        backend/src/main/java/com/foodrush/backend/dto/CartDTO.java \
        backend/src/main/java/com/foodrush/backend/dto/AddToCartRequest.java \
        backend/src/main/java/com/foodrush/backend/dto/UpdateCartItemRequest.java \
        backend/src/main/java/com/foodrush/backend/exception/CartItemNotFoundException.java \
        backend/src/main/java/com/foodrush/backend/exception/CartConflictException.java \
        backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java \
        backend/src/main/java/com/foodrush/backend/entity/Cart.java \
        backend/src/test/java/com/foodrush/backend/dto/CartDTOTest.java \
        backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: add cart DTOs and cart conflict/not-found exceptions"
```

---

### Task 2: Repositories and the cart read path

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/repository/CartRepository.java`
- Create: `backend/src/main/java/com/foodrush/backend/repository/CartItemRepository.java`
- Create: `backend/src/main/java/com/foodrush/backend/service/CartService.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java` (create)

**Interfaces:**
- Consumes: `CartDTO.from`, `CartDTO.empty` (Task 1); `Cart`, `CartItem`, `User` entities; `UserRepository`, `FoodItemRepository` (existing).
- Produces:
  - `CartRepository.findByUserId(Long userId) -> Optional<Cart>`
  - `CartItemRepository.deleteByFoodItemId(Long foodItemId) -> long` (consumed by Task 6)
  - `CartService(CartRepository, FoodItemRepository, UserRepository)` constructor
  - `CartService.getCart(Long userId) -> CartDTO`

Note the constructor does **not** take `CartItemRepository`: every line-level read happens
through `Cart.items`, and `deleteByFoodItemId` is used only by `FoodItemService` in Task 6.

- [ ] **Step 1: Write the failing read-path test**

Create `backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private UserRepository userRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, foodItemRepository, userRepository);
    }

    static Restaurant restaurant(Long id, String name) {
        return Restaurant.builder().id(id).name(name).build();
    }

    static FoodItem foodItem(Long id, String name, String price, Restaurant restaurant, boolean available) {
        return FoodItem.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal(price))
                .restaurant(restaurant)
                .isAvailable(available)
                .build();
    }

    static CartItem cartItem(Long id, FoodItem foodItem, int quantity, Cart cart) {
        CartItem item = CartItem.builder().id(id).foodItem(foodItem).quantity(quantity).cart(cart).build();
        cart.getItems().add(item);
        return item;
    }

    static Cart cart(Long id, Restaurant restaurant) {
        return Cart.builder().id(id).restaurant(restaurant).items(new ArrayList<>()).build();
    }

    @Test
    void getCart_returnsEmptyCart_whenUserHasNoCartRow() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.restaurantId()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void getCart_doesNotCreateACartRow_whenUserHasNone() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        cartService.getCart(USER_ID);

        // A GET must never write. Creating a row here would litter the table for every user
        // who merely opens the cart page.
        verify(cartRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getCart_returnsItemsWithTotal() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, true), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.restaurantName()).isEqualTo("Spice Route");
        assertThat(result.items()).hasSize(2);
        assertThat(result.total()).isEqualByComparingTo("360.00");
    }

    @Test
    void getCart_excludesUnavailableLineFromTotalButStillReturnsIt() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, false), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(1).isAvailable()).isFalse();
        assertThat(result.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void getCart_returnsNullRestaurant_whenCartIsEmpty() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart(100L, null)));

        CartDTO result = cartService.getCart(USER_ID);

        assertThat(result.restaurantId()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo("0.00");
    }
}
```

The static helpers above are reused verbatim by Tasks 3 and 4 — do not rename them.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=CartServiceTest`
Expected: BUILD FAILURE — compilation error, `CartService` and `CartRepository` do not exist.

- [ ] **Step 3: Create `CartRepository`**

Create `backend/src/main/java/com/foodrush/backend/repository/CartRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * The entity graph is required, not an optimisation: Cart.restaurant, Cart.items and
     * CartItem.foodItem are all LAZY, and building a CartDTO dereferences every one of them.
     * Fetching them in one query avoids both LazyInitializationException outside a session
     * and an N+1 select per cart line.
     */
    @EntityGraph(attributePaths = {"restaurant", "items", "items.foodItem"})
    Optional<Cart> findByUserId(Long userId);
}
```

- [ ] **Step 4: Create `CartItemRepository`**

Create `backend/src/main/java/com/foodrush/backend/repository/CartItemRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Used when a food item is hard-deleted: cart_items.food_item_id is a foreign key, so the
     * lines must go first. Cart reads all go through Cart.items instead.
     */
    long deleteByFoodItemId(Long foodItemId);
}
```

- [ ] **Step 5: Create `CartService` with the read path only**

Create `backend/src/main/java/com/foodrush/backend/service/CartService.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                        FoodItemRepository foodItemRepository,
                        UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.foodItemRepository = foodItemRepository;
        this.userRepository = userRepository;
    }

    /**
     * Read-only on purpose: a user with no cart row gets an empty representation rather than
     * a freshly inserted row.
     */
    @Transactional(readOnly = true)
    public CartDTO getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(CartDTO::from)
                .orElseGet(CartDTO::empty);
    }
}
```

`foodItemRepository` and `userRepository` are unused until Task 3. If the build fails on an
unused-field warning treated as an error, do **not** delete them — Task 3 needs both, and the
constructor signature is fixed by the `Interfaces` block above.

- [ ] **Step 6: Run to verify it passes**

Run: `./mvnw test -Dtest=CartServiceTest`
Expected: PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/repository/CartRepository.java \
        backend/src/main/java/com/foodrush/backend/repository/CartItemRepository.java \
        backend/src/main/java/com/foodrush/backend/service/CartService.java \
        backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java
git commit -m "feat: add cart repositories and read-only cart retrieval"
```

---

### Task 3: Adding items to the cart

**Files:**
- Modify: `backend/src/main/java/com/foodrush/backend/service/CartService.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java` (modify)

**Interfaces:**
- Consumes: `AddToCartRequest`, `CartConflictException`, `CartDTO` (Task 1); `CartService` constructor and `getCart` (Task 2); `FoodItemNotFoundException`, `FoodItemRepository`, `UserRepository` (existing).
- Produces: `CartService.addItemToCart(Long userId, AddToCartRequest request) -> CartDTO`.

Implements rules 1, 2 and 5, in the validation order: exists → available → restaurant matches.

- [ ] **Step 1: Write the failing add-path tests**

Append to `CartServiceTest`, inside the existing class. Also add these imports at the top of the file:

```java
import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
```

Tests:

```java
    @Test
    void addItemToCart_createsCartAndSetsRestaurant_whenUserHasNoCart() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(samosa));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(
                User.builder().id(USER_ID).name("Asha").email("asha@foodrush.com")
                        .password("hash").role(Role.USER).build()));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.addItemToCart(USER_ID, new AddToCartRequest(10L, 2));

        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).foodItemId()).isEqualTo(10L);
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        assertThat(result.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void addItemToCart_succeeds_whenItemIsFromTheSameRestaurant() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 1, cart);
        FoodItem tikka = foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, true);
        when(foodItemRepository.findById(11L)).thenReturn(Optional.of(tikka));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.addItemToCart(USER_ID, new AddToCartRequest(11L, 1));

        assertThat(result.items()).hasSize(2);
        assertThat(result.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void addItemToCart_throwsCartConflict_whenItemIsFromADifferentRestaurant() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Restaurant tandoorHouse = restaurant(2L, "Tandoor House");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 1, cart);
        when(foodItemRepository.findById(20L))
                .thenReturn(Optional.of(foodItem(20L, "Naan", "40.00", tandoorHouse, true)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, new AddToCartRequest(20L, 1)))
                .isInstanceOf(CartConflictException.class)
                .hasMessage("Cart can only contain items from one restaurant. Clear cart first?");

        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void addItemToCart_incrementsQuantity_whenItemIsAlreadyInCart() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, samosa, 2, cart);
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(samosa));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.addItemToCart(USER_ID, new AddToCartRequest(10L, 3));

        // One line, not two: quantity 2 + 3, no duplicate row.
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);
        assertThat(result.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void addItemToCart_throwsFoodItemNotFound_whenItemDoesNotExist() {
        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, new AddToCartRequest(99L, 1)))
                .isInstanceOf(FoodItemNotFoundException.class)
                .hasMessage("Food item not found: 99");
    }

    @Test
    void addItemToCart_throwsCartConflict_whenItemIsUnavailable() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        when(foodItemRepository.findById(10L))
                .thenReturn(Optional.of(foodItem(10L, "Samosa", "60.00", spiceRoute, false)));

        assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, new AddToCartRequest(10L, 1)))
                .isInstanceOf(CartConflictException.class)
                .hasMessage("Food item is not available: Samosa");

        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void addItemToCart_reportsUnavailabilityBeforeRestaurantConflict() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Restaurant tandoorHouse = restaurant(2L, "Tandoor House");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 1, cart);
        // Unavailable AND from another restaurant: availability is a property of the item
        // alone, so it wins over a conflict the user could clear.
        when(foodItemRepository.findById(20L))
                .thenReturn(Optional.of(foodItem(20L, "Naan", "40.00", tandoorHouse, false)));

        assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, new AddToCartRequest(20L, 1)))
                .isInstanceOf(CartConflictException.class)
                .hasMessage("Food item is not available: Naan");
    }

    @Test
    void addItemToCart_setsRestaurantOnAnEmptyCartThatStillHasARow() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, null);
        when(foodItemRepository.findById(10L))
                .thenReturn(Optional.of(foodItem(10L, "Samosa", "60.00", spiceRoute, true)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.addItemToCart(USER_ID, new AddToCartRequest(10L, 1));

        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.restaurantName()).isEqualTo("Spice Route");
    }

    @Test
    void addItemToCart_attachesTheAuthenticatedUserToANewCart() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        User asha = User.builder().id(USER_ID).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build();
        when(foodItemRepository.findById(10L))
                .thenReturn(Optional.of(foodItem(10L, "Samosa", "60.00", spiceRoute, true)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(asha));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cartService.addItemToCart(USER_ID, new AddToCartRequest(10L, 1));

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getUser()).isEqualTo(asha);
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=CartServiceTest`
Expected: BUILD FAILURE — compilation error, `addItemToCart` does not exist.

- [ ] **Step 3: Implement `addItemToCart`**

In `CartService`, add these imports:

```java
import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.FoodItemNotFoundException;

import java.util.Optional;
```

Add the message constant at the top of the class body, above the fields:

```java
    private static final String DIFFERENT_RESTAURANT_MESSAGE =
            "Cart can only contain items from one restaurant. Clear cart first?";
```

Add the method after `getCart`:

```java
    /**
     * Validation order matters: existence, then availability, then the single-restaurant rule.
     * Availability is a property of the item alone, while a restaurant conflict depends on
     * cart state the user can clear - so the more fundamental problem is reported first.
     */
    @Transactional
    public CartDTO addItemToCart(Long userId, AddToCartRequest request) {
        FoodItem foodItem = foodItemRepository.findById(request.foodItemId())
                .orElseThrow(() -> new FoodItemNotFoundException(
                        "Food item not found: " + request.foodItemId()));
        if (!foodItem.isAvailable()) {
            throw new CartConflictException("Food item is not available: " + foodItem.getName());
        }

        Cart cart = getOrCreateCart(userId);
        Restaurant cartRestaurant = cart.getRestaurant();
        if (cartRestaurant != null && !cartRestaurant.getId().equals(foodItem.getRestaurant().getId())) {
            throw new CartConflictException(DIFFERENT_RESTAURANT_MESSAGE);
        }
        cart.setRestaurant(foodItem.getRestaurant());

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(item -> item.getFoodItem().getId().equals(foodItem.getId()))
                .findFirst();
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.quantity());
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .foodItem(foodItem)
                    .quantity(request.quantity())
                    .build());
        }
        return CartDTO.from(cartRepository.save(cart));
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Authenticated user not found: " + userId));
                    return cartRepository.save(Cart.builder().user(user).build());
                });
    }
```

Add the import `com.foodrush.backend.entity.Restaurant` as well.

The `IllegalStateException` is deliberate: an authenticated request whose user row has vanished
is a server-side inconsistency, not client error, and the existing generic handler turns it
into a 500.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=CartServiceTest`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/CartService.java \
        backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java
git commit -m "feat: add items to cart with single-restaurant and availability rules"
```

---

### Task 4: Updating, removing and clearing cart lines

**Files:**
- Modify: `backend/src/main/java/com/foodrush/backend/service/CartService.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java` (modify)

**Interfaces:**
- Consumes: everything from Tasks 1–3; `CartItemNotFoundException` (Task 1).
- Produces:
  - `CartService.updateCartItem(Long userId, Long cartItemId, int quantity) -> CartDTO`
  - `CartService.removeCartItem(Long userId, Long cartItemId) -> CartDTO`
  - `CartService.clearCart(Long userId) -> CartDTO`

Implements rules 3, 4 and 7.

- [ ] **Step 1: Write the failing mutation tests**

Append to `CartServiceTest`, and add the import `com.foodrush.backend.exception.CartItemNotFoundException`:

```java
    @Test
    void updateCartItem_changesQuantity() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.updateCartItem(USER_ID, 1L, 5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);
        assertThat(result.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void updateCartItem_removesLine_whenQuantityIsZero() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, true), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.updateCartItem(USER_ID, 1L, 0);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).cartItemId()).isEqualTo(2L);
        assertThat(result.total()).isEqualByComparingTo("240.00");
    }

    @Test
    void updateCartItem_resetsRestaurant_whenZeroRemovesTheLastLine() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.updateCartItem(USER_ID, 1L, 0);

        // Otherwise the user stays locked to Spice Route with an empty cart.
        assertThat(result.restaurantId()).isNull();
        assertThat(cart.getRestaurant()).isNull();
    }

    @Test
    void updateCartItem_throwsCartItemNotFound_whenLineBelongsToAnotherUser() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // 42 exists in the database but sits in somebody else's cart. Scanning only this
        // user's own lines makes it indistinguishable from a nonexistent id - no leak.
        assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, 42L, 3))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessage("Cart item not found: 42");
    }

    @Test
    void updateCartItem_throwsCartItemNotFound_whenUserHasNoCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, 1L, 3))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessage("Cart item not found: 1");
    }

    @Test
    void removeCartItem_deletesTheLine() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, true), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.removeCartItem(USER_ID, 2L);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).cartItemId()).isEqualTo(1L);
        assertThat(result.restaurantId()).isEqualTo(1L);
    }

    @Test
    void removeCartItem_resetsRestaurant_whenLastLineIsRemoved() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.removeCartItem(USER_ID, 1L);

        assertThat(result.items()).isEmpty();
        assertThat(result.restaurantId()).isNull();
        assertThat(result.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void removeCartItem_throwsCartItemNotFound_whenLineBelongsToAnotherUser() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.removeCartItem(USER_ID, 42L))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessage("Cart item not found: 42");
    }

    @Test
    void clearCart_removesAllLinesAndResetsRestaurant() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 2, cart);
        cartItem(2L, foodItem(11L, "Paneer Tikka", "240.00", spiceRoute, true), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDTO result = cartService.clearCart(USER_ID);

        assertThat(result.items()).isEmpty();
        assertThat(result.restaurantId()).isNull();
        assertThat(result.total()).isEqualByComparingTo("0.00");
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void clearCart_isANoOp_whenUserHasNoCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartDTO result = cartService.clearCart(USER_ID);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo("0.00");
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void clearCart_allowsAddingFromADifferentRestaurantAfterwards() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Restaurant tandoorHouse = restaurant(2L, "Tandoor House");
        Cart cart = cart(100L, spiceRoute);
        cartItem(1L, foodItem(10L, "Samosa", "60.00", spiceRoute, true), 1, cart);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foodItemRepository.findById(20L))
                .thenReturn(Optional.of(foodItem(20L, "Naan", "40.00", tandoorHouse, true)));

        cartService.clearCart(USER_ID);
        CartDTO result = cartService.addItemToCart(USER_ID, new AddToCartRequest(20L, 1));

        // This is the whole point of rule 4 - the 409 message tells the user to clear the
        // cart, so clearing it must actually unblock them.
        assertThat(result.restaurantId()).isEqualTo(2L);
        assertThat(result.items()).hasSize(1);
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=CartServiceTest`
Expected: BUILD FAILURE — compilation error, `updateCartItem` / `removeCartItem` / `clearCart` do not exist.

- [ ] **Step 3: Implement the three mutations**

In `CartService`, add the import `com.foodrush.backend.exception.CartItemNotFoundException` and append these methods after `addItemToCart`:

```java
    @Transactional
    public CartDTO updateCartItem(Long userId, Long cartItemId, int quantity) {
        Cart cart = requireCart(userId, cartItemId);
        CartItem item = requireOwnedItem(cart, cartItemId);
        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(quantity);
        }
        return saveAndConvert(cart);
    }

    @Transactional
    public CartDTO removeCartItem(Long userId, Long cartItemId) {
        Cart cart = requireCart(userId, cartItemId);
        cart.getItems().remove(requireOwnedItem(cart, cartItemId));
        return saveAndConvert(cart);
    }

    @Transactional
    public CartDTO clearCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    cart.getItems().clear();
                    return saveAndConvert(cart);
                })
                .orElseGet(CartDTO::empty);
    }

    private Cart requireCart(Long userId, Long cartItemId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + cartItemId));
    }

    /**
     * Searching only within the caller's own cart is what enforces ownership: another user's
     * cart item id simply is not here, and reports as 404 exactly like a nonexistent one, so
     * existence is never leaked.
     */
    private CartItem requireOwnedItem(Cart cart, Long cartItemId) {
        return cart.getItems().stream()
                .filter(item -> cartItemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + cartItemId));
    }

    /**
     * An empty cart releases its restaurant, so a user who empties their cart is not left
     * locked to the restaurant they just abandoned.
     */
    private CartDTO saveAndConvert(Cart cart) {
        if (cart.getItems().isEmpty()) {
            cart.setRestaurant(null);
        }
        return CartDTO.from(cartRepository.save(cart));
    }
```

Then refactor `addItemToCart` to end with `return saveAndConvert(cart);` instead of
`return CartDTO.from(cartRepository.save(cart));` — behaviour is identical there because the
cart is never empty at that point, and it keeps one save path.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=CartServiceTest`
Expected: PASS, 25 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/CartService.java \
        backend/src/test/java/com/foodrush/backend/service/CartServiceTest.java
git commit -m "feat: add cart item update, removal and clear with restaurant reset"
```

---

### Task 5: `CartController` — the five endpoints

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/controller/CartController.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/CartControllerTest.java`

**Interfaces:**
- Consumes: `CartService.getCart/addItemToCart/updateCartItem/removeCartItem/clearCart` (Tasks 2–4); `CartDTO`, `CartItemDTO`, `AddToCartRequest`, `UpdateCartItemRequest` (Task 1); `UserPrincipal` (existing).
- Produces: `GET /api/cart` → 200; `POST /api/cart/items` → 200; `PUT /api/cart/items/{cartItemId}` → 200; `DELETE /api/cart/items/{cartItemId}` → 200; `DELETE /api/cart` → 200. All bodies are `CartDTO`. All are already `authenticated()` via the existing `/api/cart/**` matcher.

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/com/foodrush/backend/controller/CartControllerTest.java`:

```java
package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.dto.CartItemDTO;
import com.foodrush.backend.dto.UpdateCartItemRequest;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.CartItemNotFoundException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CartService cartService;

    /*
     * Security filters are disabled, so nothing populates the SecurityContext for us - but
     * @AuthenticationPrincipal still reads from SecurityContextHolder. Seed it directly.
     */
    @BeforeEach
    void setUpPrincipal() {
        UserPrincipal principal = new UserPrincipal(User.builder()
                .id(USER_ID).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    private CartDTO sampleCart() {
        return new CartDTO(1L, "Spice Route", List.of(
                new CartItemDTO(1L, 10L, "Samosa", new BigDecimal("60.00"), 2,
                        new BigDecimal("120.00"), true)),
                new BigDecimal("120.00"));
    }

    @Test
    void getCart_returns200WithCartBody() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(sampleCart());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.restaurantName").value("Spice Route"))
                .andExpect(jsonPath("$.items[0].cartItemId").value(1))
                .andExpect(jsonPath("$.items[0].foodItemId").value(10))
                .andExpect(jsonPath("$.items[0].name").value("Samosa"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(120.00))
                .andExpect(jsonPath("$.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.total").value(120.00));
    }

    @Test
    void getCart_returns200WithEmptyCart_whenUserHasNone() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(CartDTO.empty());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                // isEmpty(), not doesNotExist(): Jackson writes the null field as an explicit
                // "restaurantId": null, so the path exists and only its value is empty.
                .andExpect(jsonPath("$.restaurantId").isEmpty())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0.00));
    }

    @Test
    void addItem_returns200AndPassesTheAuthenticatedUserId() throws Exception {
        when(cartService.addItemToCart(eq(USER_ID), any(AddToCartRequest.class))).thenReturn(sampleCart());

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(10L, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(120.00));

        verify(cartService).addItemToCart(eq(USER_ID), any(AddToCartRequest.class));
    }

    @Test
    void addItem_returns400_whenFoodItemIdMissing() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(null, 2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_returns400_whenQuantityIsZero() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(10L, 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_returns404_whenFoodItemDoesNotExist() throws Exception {
        when(cartService.addItemToCart(eq(USER_ID), any(AddToCartRequest.class)))
                .thenThrow(new FoodItemNotFoundException("Food item not found: 99"));

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(99L, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_returns409_whenItemIsFromADifferentRestaurant() throws Exception {
        when(cartService.addItemToCart(eq(USER_ID), any(AddToCartRequest.class)))
                .thenThrow(new CartConflictException(
                        "Cart can only contain items from one restaurant. Clear cart first?"));

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCartRequest(20L, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cart can only contain items from one restaurant. Clear cart first?"));
    }

    @Test
    void updateItem_returns200() throws Exception {
        when(cartService.updateCartItem(USER_ID, 1L, 5)).thenReturn(sampleCart());

        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(5))))
                .andExpect(status().isOk());

        verify(cartService).updateCartItem(USER_ID, 1L, 5);
    }

    @Test
    void updateItem_acceptsQuantityZeroAsRemoval() throws Exception {
        when(cartService.updateCartItem(USER_ID, 1L, 0)).thenReturn(CartDTO.empty());

        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void updateItem_returns400_whenQuantityIsNegative() throws Exception {
        mockMvc.perform(put("/api/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(-1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateItem_returns404_whenLineIsNotInTheUsersCart() throws Exception {
        when(cartService.updateCartItem(USER_ID, 42L, 3))
                .thenThrow(new CartItemNotFoundException("Cart item not found: 42"));

        mockMvc.perform(put("/api/cart/items/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_returns200WithUpdatedCart() throws Exception {
        when(cartService.removeCartItem(USER_ID, 1L)).thenReturn(CartDTO.empty());

        mockMvc.perform(delete("/api/cart/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.00));

        verify(cartService).removeCartItem(USER_ID, 1L);
    }

    @Test
    void removeItem_returns404_whenLineIsNotInTheUsersCart() throws Exception {
        when(cartService.removeCartItem(USER_ID, 42L))
                .thenThrow(new CartItemNotFoundException("Cart item not found: 42"));

        mockMvc.perform(delete("/api/cart/items/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_returns200WithEmptyCart() throws Exception {
        when(cartService.clearCart(USER_ID)).thenReturn(CartDTO.empty());

        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0.00));

        verify(cartService).clearCart(USER_ID);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=CartControllerTest`
Expected: BUILD FAILURE — compilation error, `CartController` does not exist.

- [ ] **Step 3: Create `CartController`**

Create `backend/src/main/java/com/foodrush/backend/controller/CartController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.dto.UpdateCartItemRequest;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint returns the full cart so the client always holds a recalculated total and
 * never needs a follow-up GET. That is also why the deletes return 200 with a body, not 204.
 *
 * Do not add a GET /api/cart/{something} mapping here: the test-only ProbeController maps
 * GET /api/cart/probe, and the two would collide ambiguously under @SpringBootTest.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartDTO> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDTO> addItem(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItemToCart(principal.getId(), request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartDTO> updateItem(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long cartItemId,
                                               @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(
                cartService.updateCartItem(principal.getId(), cartItemId, request.quantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartDTO> removeItem(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long cartItemId) {
        return ResponseEntity.ok(cartService.removeCartItem(principal.getId(), cartItemId));
    }

    @DeleteMapping
    public ResponseEntity<CartDTO> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.clearCart(principal.getId()));
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=CartControllerTest`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/controller/CartController.java \
        backend/src/test/java/com/foodrush/backend/controller/CartControllerTest.java
git commit -m "feat: add cart REST endpoints"
```

---

### Task 6: Purge cart lines when a food item is hard-deleted

**Files:**
- Modify: `backend/src/main/java/com/foodrush/backend/service/FoodItemService.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java`

**Interfaces:**
- Consumes: `CartItemRepository.deleteByFoodItemId(Long)` (Task 2).
- Produces: no new public API. `FoodItemService`'s constructor gains a fifth parameter:
  `FoodItemService(FoodItemRepository, RestaurantRepository, CategoryRepository, OrderItemRepository, CartItemRepository)`.

This is the cleanup the Task 4 plan explicitly deferred here: `cart_items.food_item_id` is a
foreign key, so hard-deleting a food item that sits in someone's cart currently raises
`DataIntegrityViolationException` and surfaces as a confusing 409 on a legitimate admin delete.

- [ ] **Step 1: Write the failing tests**

In `backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java`, add the import
`com.foodrush.backend.repository.CartItemRepository`, add the mock field, and update `setUp`:

```java
    @Mock
    private CartItemRepository cartItemRepository;
```

```java
    @BeforeEach
    void setUp() {
        foodItemService = new FoodItemService(
                foodItemRepository, restaurantRepository, categoryRepository, orderItemRepository,
                cartItemRepository);
    }
```

Then append these two tests:

```java
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
    void deleteFoodItem_leavesCartLinesAlone_whenSoftDeleting() {
        FoodItem existing = foodItem(1L, "Samosa", 10L, "Appetizers", "60.00");
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.existsByFoodItemId(1L)).thenReturn(true);

        foodItemService.deleteFoodItem(1L);

        // The row still exists, so carts holding it stay valid - the line simply reports
        // isAvailable false and drops out of the cart total.
        assertThat(existing.isAvailable()).isFalse();
        verify(cartItemRepository, never()).deleteByFoodItemId(any());
    }
```

Add these imports to the test file:

```java
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=FoodItemServiceTest`
Expected: BUILD FAILURE — compilation error, the `FoodItemService` constructor takes 4 arguments, not 5.

- [ ] **Step 3: Wire `CartItemRepository` into `FoodItemService`**

In `backend/src/main/java/com/foodrush/backend/service/FoodItemService.java`, add the import
`com.foodrush.backend.repository.CartItemRepository`, add the field, and extend the constructor:

```java
    private final CartItemRepository cartItemRepository;

    public FoodItemService(FoodItemRepository foodItemRepository,
                            RestaurantRepository restaurantRepository,
                            CategoryRepository categoryRepository,
                            OrderItemRepository orderItemRepository,
                            CartItemRepository cartItemRepository) {
        this.foodItemRepository = foodItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
    }
```

- [ ] **Step 4: Purge cart lines in `deleteFoodItem`**

Replace the body of `deleteFoodItem` with:

```java
    /**
     * Soft-deletes (marks unavailable) any item that already appears in order history, so past
     * orders keep referring to a real row. Items with no order history are removed outright -
     * and because cart_items.food_item_id is a foreign key, any cart lines holding the item
     * must go first or the delete fails with a constraint violation.
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
        foodItemRepository.delete(foodItem);
    }
```

- [ ] **Step 5: Run to verify it passes**

Run: `./mvnw test -Dtest=FoodItemServiceTest`
Expected: PASS, all existing tests plus the 2 new ones.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/FoodItemService.java \
        backend/src/test/java/com/foodrush/backend/service/FoodItemServiceTest.java
git commit -m "fix: purge cart lines before hard-deleting a food item"
```

---

### Task 7: Full-suite verification and task status

**Files:**
- Modify: `.taskmaster/tasks/tasks.json` (via the Task Master CLI/MCP only — never hand-edit)

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: a green suite and Task 5 marked done.

- [ ] **Step 1: Export the environment and run the whole suite**

From `backend/`, with the repo's `.env` values exported into the shell:

```bash
set -a && . ../.env && set +a && ./mvnw test
```

Expected: BUILD SUCCESS. Every previously passing test still passes, plus the new
`CartDTOTest`, `CartServiceTest` and `CartControllerTest`.

If `contextLoads` fails on an unresolved `${DB_USERNAME}`, the `.env` values were not exported —
that is an environment problem, not a code problem. Fix the export and re-run before
investigating anything else.

- [ ] **Step 2: Confirm the probe mappings still resolve**

Run: `./mvnw test -Dtest=SecurityConfigTest`
Expected: PASS. This is the regression check for the `ProbeController` collision constraint —
`GET /api/cart/probe` must still resolve unambiguously alongside the new `GET /api/cart`.

- [ ] **Step 3: Mark the task done**

```bash
task-master set-status --id=5 --status=done
```

- [ ] **Step 4: Commit**

```bash
git add .taskmaster/tasks/tasks.json
git commit -m "chore(taskmaster): mark Task 5 (shopping cart APIs) as done"
```

---

## Requirement coverage

Every requirement from `tasks.json` Task 5 and the spec, mapped to where it is implemented and asserted.

| Requirement | Implemented | Asserted |
|---|---|---|
| `CartRepository.findByUserId` | Task 2 Step 3 | Task 2 Step 1 (via service) |
| `CartItemRepository` | Task 2 Step 4 | Task 6 Step 1 |
| `getOrCreateCart` | Task 3 Step 3 | Task 3 Step 1 (`...whenUserHasNoCart`, `...attachesTheAuthenticatedUser...`) |
| `addItemToCart` | Task 3 Step 3 | Task 3 Step 1 (9 tests) |
| `updateCartItem` | Task 4 Step 3 | Task 4 Step 1 (5 tests) |
| `removeCartItem` | Task 4 Step 3 | Task 4 Step 1 (3 tests) |
| `clearCart` | Task 4 Step 3 | Task 4 Step 1 (3 tests) |
| `calculateTotal` | Task 1 Step 4 (`CartDTO.from`) | Task 1 Step 1, Task 2 Step 1 |
| `GET /api/cart` | Task 5 Step 3 | Task 5 Step 1 |
| `POST /api/cart/items` | Task 5 Step 3 | Task 5 Step 1 |
| `PUT /api/cart/items/{id}` | Task 5 Step 3 | Task 5 Step 1 |
| `DELETE /api/cart/items/{id}` | Task 5 Step 3 | Task 5 Step 1 |
| `DELETE /api/cart` | Task 5 Step 3 | Task 5 Step 1 |
| Authenticated user ID extraction | Task 5 Step 3 | Task 5 Step 1 (`...PassesTheAuthenticatedUserId`) |
| `CartDTO`, `CartItemDTO`, `AddToCartRequest` | Task 1 | Task 1 Step 1 |
| Rule 1 single restaurant | Task 3 Step 3 | Task 3 Step 1 |
| Rule 2 merge duplicates | Task 3 Step 3 | Task 3 Step 1 |
| Rule 3 quantity 0 removes | Task 4 Step 3 | Task 4 Step 1 |
| Rule 4 empty cart resets restaurant | Task 4 Step 3 | Task 4 Step 1 (3 tests) |
| Rule 5 unavailable cannot be added | Task 3 Step 3 | Task 3 Step 1 |
| Rule 6 unavailable lines flagged | Task 1 Step 4 | Task 1 Step 1, Task 2 Step 1 |
| Rule 7 ownership → 404 | Task 4 Step 3 | Task 4 Step 1, Task 5 Step 1 |
| Rule 8 total | Task 1 Step 4 | Task 1 Step 1 |
| Validation order on add | Task 3 Step 3 | Task 3 Step 1 (`...reportsUnavailabilityBeforeRestaurantConflict`) |
| Task 4 handoff: purge cart lines | Task 6 Steps 3–4 | Task 6 Step 1 |
| 401 for unauthenticated | pre-existing `SecurityConfig` | pre-existing `SecurityConfigTest`, re-run in Task 7 Step 2 |
