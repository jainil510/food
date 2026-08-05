# Order Placement and Checkout APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Task 7 (tasks.json) — convert a user's cart into an `Order`, expose order history and order detail endpoints, and clear the cart on success.

**Architecture:** Follows the existing `controller -> service -> repository -> entity` layering used by `CartService`/`AddressService`. `OrderService.placeOrder` reads the caller's `Cart` (already `@EntityGraph`-fetched via `CartRepository.findByUserId`), the caller's `Address` (via `AddressRepository.findByUserIdAndId`, already ownership-scoped), builds one `Order` + N `OrderItem` rows with `priceAtOrder` snapshotted from `FoodItem.price`, saves it, then clears the cart exactly like `CartService.clearCart` does.

**Tech Stack:** Spring Boot 4.1.0, Java 21, Spring Data JPA, Spring Security (JWT), Lombok, JUnit 5 + Mockito + AssertJ, MockMvc for controller slice tests.

## Global Constraints

- Ownership violations (address/order belonging to another user) return **404**, not 403 — this repo's established pattern (see `CartService.requireOwnedItem`, `AddressService.requireOwnedAddress`) is to make another user's resource indistinguishable from a nonexistent one, so existence is never leaked. This intentionally supersedes the literal "403" wording in tasks.json's test strategy for task 7.
- `OrderRepository` and `OrderItemRepository` already exist (`repository/OrderRepository.java`, `repository/OrderItemRepository.java`) with one method each already in use by `RestaurantService`/`FoodItemService` — extend them, don't replace them.
- `Order`/`OrderItem`/`OrderStatus` entities already exist and are schema-validated — do not modify their fields.
- New endpoints live under `/api/orders/**`, already `authenticated()` in `SecurityConfig` — no security config changes needed.
- Money fields: `BigDecimal`, `setScale(2, RoundingMode.HALF_UP)` on totals, matching `CartDTO.from`.

---

### Task 1: Exceptions for checkout failure modes

**Files:**
- Create: `src/main/java/com/foodrush/backend/exception/EmptyCartException.java`
- Create: `src/main/java/com/foodrush/backend/exception/OrderNotFoundException.java`
- Modify: `src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `EmptyCartException(String message)` → 400; `OrderNotFoundException(String message)` → 404. `CartConflictException` (already exists) is reused for "cart item no longer available" at checkout → 409.

- [ ] **Step 1: Create the two new exception classes**

```java
package com.foodrush.backend.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(String message) {
        super(message);
    }
}
```

```java
package com.foodrush.backend.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Register handlers in GlobalExceptionHandler**

Add alongside the existing `AddressNotFoundException` handler:

```java
    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ErrorResponse> handleEmptyCart(EmptyCartException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }
```

- [ ] **Step 3: Compile check**

Run: `./mvnw -q compile` from `backend/`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/exception/
git commit -m "feat: add EmptyCartException and OrderNotFoundException for checkout"
```

---

### Task 2: Order DTOs

**Files:**
- Create: `src/main/java/com/foodrush/backend/dto/PlaceOrderRequest.java`
- Create: `src/main/java/com/foodrush/backend/dto/OrderItemDTO.java`
- Create: `src/main/java/com/foodrush/backend/dto/OrderDTO.java`
- Create: `src/main/java/com/foodrush/backend/dto/OrderDetailDTO.java`
- Test: `src/test/java/com/foodrush/backend/dto/OrderDTOTest.java` (mirrors `CartDTOTest.java` style — tests the `from()` mapping only, no mocks needed since these are pure records)

**Interfaces:**
- Consumes: `Order`, `OrderItem`, `OrderStatus` entities (Task work already done — see Global Constraints); `AddressDTO.from(Address)` (existing).
- Produces: `PlaceOrderRequest.addressId(): Long`; `OrderDTO.from(Order): OrderDTO`; `OrderDetailDTO.from(Order): OrderDetailDTO`; `OrderItemDTO.from(OrderItem): OrderItemDTO`. `OrderService` (Task 4) and `OrderController` (Task 5) depend on these exact names.

- [ ] **Step 1: Write PlaceOrderRequest**

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
        @NotNull(message = "Address ID is required")
        Long addressId
) {
}
```

- [ ] **Step 2: Write OrderItemDTO**

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrderItemDTO(
        Long foodItemId,
        String foodItemName,
        Integer quantity,
        BigDecimal priceAtOrder,
        BigDecimal subtotal
) {

    public static OrderItemDTO from(OrderItem item) {
        BigDecimal subtotal = item.getPriceAtOrder()
                .multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new OrderItemDTO(
                item.getFoodItem().getId(),
                item.getFoodItem().getName(),
                item.getQuantity(),
                item.getPriceAtOrder(),
                subtotal);
    }
}
```

- [ ] **Step 3: Write OrderDTO (order history entry / placement confirmation)**

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDTO(
        Long id,
        Long restaurantId,
        String restaurantName,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt
) {

    public static OrderDTO from(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getRestaurant().getId(),
                order.getRestaurant().getName(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
```

- [ ] **Step 4: Write OrderDetailDTO**

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailDTO(
        Long id,
        String restaurantName,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        AddressDTO deliveryAddress,
        List<OrderItemDTO> items
) {

    public static OrderDetailDTO from(Order order) {
        return new OrderDetailDTO(
                order.getId(),
                order.getRestaurant().getName(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                AddressDTO.from(order.getAddress()),
                order.getItems().stream().map(OrderItemDTO::from).toList());
    }
}
```

- [ ] **Step 5: Write the mapping test**

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderItem;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDTOTest {

    @Test
    void orderDTO_mapsRestaurantAndTotals() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Spice Route").build();
        Order order = Order.builder()
                .id(50L).restaurant(restaurant)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 12, 0)).build();

        OrderDTO dto = OrderDTO.from(order);

        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.restaurantId()).isEqualTo(1L);
        assertThat(dto.restaurantName()).isEqualTo("Spice Route");
        assertThat(dto.totalAmount()).isEqualByComparingTo("120.00");
        assertThat(dto.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void orderDetailDTO_mapsAddressAndItems() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Spice Route").build();
        Address address = Address.builder().id(9L).label("Home").fullAddress("221B Baker Street")
                .city("Mumbai").pincode("400001").build();
        FoodItem samosa = FoodItem.builder().id(10L).name("Samosa").build();
        Order order = Order.builder()
                .id(50L).restaurant(restaurant).address(address)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 12, 0))
                .items(List.of(OrderItem.builder()
                        .foodItem(samosa).quantity(2).priceAtOrder(new BigDecimal("60.00")).build()))
                .build();

        OrderDetailDTO dto = OrderDetailDTO.from(order);

        assertThat(dto.restaurantName()).isEqualTo("Spice Route");
        assertThat(dto.deliveryAddress().id()).isEqualTo(9L);
        assertThat(dto.deliveryAddress().city()).isEqualTo("Mumbai");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).foodItemName()).isEqualTo("Samosa");
        assertThat(dto.items().get(0).subtotal()).isEqualByComparingTo("120.00");
    }
}
```

- [ ] **Step 6: Run the test**

Run: `./mvnw -q test -Dtest=OrderDTOTest` from `backend/`
Expected: PASS (2 tests)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/dto/PlaceOrderRequest.java backend/src/main/java/com/foodrush/backend/dto/OrderItemDTO.java backend/src/main/java/com/foodrush/backend/dto/OrderDTO.java backend/src/main/java/com/foodrush/backend/dto/OrderDetailDTO.java backend/src/test/java/com/foodrush/backend/dto/OrderDTOTest.java
git commit -m "feat: add order DTOs and mapping tests"
```

---

### Task 3: Repository query methods

**Files:**
- Modify: `src/main/java/com/foodrush/backend/repository/OrderRepository.java`

**Interfaces:**
- Produces: `OrderRepository.findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable): Page<Order>`; `OrderRepository.findByUserIdAndId(Long userId, Long id): Optional<Order>`. `OrderService` (Task 4) depends on both exact names.

- [ ] **Step 1: Add the two derived-query methods**

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<OrderStatus> excludedStatuses);

    @EntityGraph(attributePaths = {"restaurant"})
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"restaurant", "address", "items", "items.foodItem"})
    Optional<Order> findByUserIdAndId(Long userId, Long id);
}
```

(The `@EntityGraph` on both methods mirrors `CartRepository.findByUserId`'s reasoning: `Order.restaurant`/`address` and `OrderItem.foodItem` are all `LAZY`, and `OrderDTO.from`/`OrderDetailDTO.from` dereference them outside of a guaranteed-open session otherwise.)

- [ ] **Step 2: Compile check**

Run: `./mvnw -q compile` from `backend/`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/repository/OrderRepository.java
git commit -m "feat: add user-scoped order history and lookup queries"
```

---

### Task 4: OrderService

**Files:**
- Create: `src/main/java/com/foodrush/backend/service/OrderService.java`
- Test: `src/test/java/com/foodrush/backend/service/OrderServiceTest.java` (mirrors `CartServiceTest.java` style: `@ExtendWith(MockitoExtension.class)`, `@Mock` repositories, no Spring context)

**Interfaces:**
- Consumes: `CartRepository.findByUserId(Long): Optional<Cart>` (existing), `AddressRepository.findByUserIdAndId(Long, Long): Optional<Address>` (existing), `OrderRepository` methods from Task 3, `OrderDTO.from`/`OrderDetailDTO.from` from Task 2, `EmptyCartException`/`OrderNotFoundException`/`CartConflictException`/`AddressNotFoundException` from Task 1.
- Produces: `OrderService.placeOrder(Long userId, Long addressId): OrderDTO`; `OrderService.getUserOrders(Long userId, int page, int size): PagedResponse<OrderDTO>`; `OrderService.getOrderById(Long userId, Long orderId): OrderDetailDTO`. `OrderController` (Task 5) depends on these exact names.

- [ ] **Step 1: Write the failing tests**

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.EmptyCartException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.repository.AddressRepository;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AddressRepository addressRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, addressRepository);
    }

    static User user(Long id) {
        return User.builder().id(id).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build();
    }

    static Restaurant restaurant(Long id, String name) {
        return Restaurant.builder().id(id).name(name).build();
    }

    static FoodItem foodItem(Long id, String name, String price, Restaurant restaurant, boolean available) {
        return FoodItem.builder().id(id).name(name).price(new BigDecimal(price))
                .restaurant(restaurant).isAvailable(available).build();
    }

    static Cart cartWithItems(User user, Restaurant restaurant, CartItem... items) {
        Cart cart = Cart.builder().id(100L).user(user).restaurant(restaurant)
                .items(new ArrayList<>(List.of(items))).build();
        return cart;
    }

    static CartItem cartItem(FoodItem foodItem, int quantity) {
        return CartItem.builder().id(1L).foodItem(foodItem).quantity(quantity).build();
    }

    static Address address(Long id, User user) {
        return Address.builder().id(id).user(user).label("Home")
                .fullAddress("221B Baker Street").city("Mumbai").pincode("400001").build();
    }

    @Test
    void placeOrder_createsOrderFromCartAndClearsCart() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Cart cart = cartWithItems(asha, spiceRoute, cartItem(samosa, 2));
        Address address = address(9L, asha);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 9L)).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(50L);
            return order;
        });

        OrderDTO result = orderService.placeOrder(USER_ID, 9L);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.totalAmount()).isEqualByComparingTo("120.00");
        assertThat(result.status()).isEqualTo(OrderStatus.PLACED);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getPriceAtOrder()).isEqualByComparingTo("60.00");
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getAddress()).isEqualTo(address);

        // Cart must be cleared after a successful order.
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getRestaurant()).isNull();
        verify(cartRepository).save(cart);
    }

    @Test
    void placeOrder_throwsEmptyCart_whenCartHasNoItems() {
        Cart cart = cartWithItems(user(USER_ID), null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 9L))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_throwsEmptyCart_whenUserHasNoCartRow() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 9L))
                .isInstanceOf(EmptyCartException.class);
    }

    @Test
    void placeOrder_throwsAddressNotFound_whenAddressDoesNotBelongToUser() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cartWithItems(asha, spiceRoute,
                cartItem(foodItem(10L, "Samosa", "60.00", spiceRoute, true), 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 42L))
                .isInstanceOf(AddressNotFoundException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_throwsCartConflict_whenAnItemBecameUnavailable() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Cart cart = cartWithItems(asha, spiceRoute,
                cartItem(foodItem(10L, "Samosa", "60.00", spiceRoute, false), 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 9L)).thenReturn(Optional.of(address(9L, asha)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, 9L))
                .isInstanceOf(CartConflictException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_snapshotsCurrentPrice_notALaterPrice() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Cart cart = cartWithItems(asha, spiceRoute, cartItem(samosa, 3));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserIdAndId(USER_ID, 9L)).thenReturn(Optional.of(address(9L, asha)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.placeOrder(USER_ID, 9L);
        // Price changes after the order is placed - already-placed order must not move.
        samosa.setPrice(new BigDecimal("999.00"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getPriceAtOrder()).isEqualByComparingTo("60.00");
    }

    @Test
    void getUserOrders_returnsPagedHistory() {
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Order order = Order.builder().id(50L).restaurant(spiceRoute)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED).build();
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any())).thenReturn(page);

        PagedResponse<OrderDTO> result = orderService.getUserOrders(USER_ID, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(50L);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getOrderById_returnsDetail_whenOrderBelongsToUser() {
        User asha = user(USER_ID);
        Restaurant spiceRoute = restaurant(1L, "Spice Route");
        Address address = address(9L, asha);
        FoodItem samosa = foodItem(10L, "Samosa", "60.00", spiceRoute, true);
        Order order = Order.builder().id(50L).restaurant(spiceRoute).address(address)
                .totalAmount(new BigDecimal("120.00")).status(OrderStatus.PLACED)
                .items(List.of(com.foodrush.backend.entity.OrderItem.builder()
                        .foodItem(samosa).quantity(2).priceAtOrder(new BigDecimal("60.00")).build()))
                .build();
        when(orderRepository.findByUserIdAndId(USER_ID, 50L)).thenReturn(Optional.of(order));

        OrderDetailDTO result = orderService.getOrderById(USER_ID, 50L);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.deliveryAddress().id()).isEqualTo(9L);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void getOrderById_throwsOrderNotFound_whenOrderBelongsToAnotherUser() {
        // 99 exists in the database but under a different user id - scoping the lookup to the
        // caller's own user id makes it indistinguishable from a nonexistent id, no leak.
        when(orderRepository.findByUserIdAndId(USER_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(USER_ID, 99L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=OrderServiceTest` from `backend/`
Expected: FAIL — `OrderService` does not exist yet

- [ ] **Step 3: Write OrderService**

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderItem;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.EmptyCartException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.repository.AddressRepository;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;

    public OrderService(OrderRepository orderRepository,
                         CartRepository cartRepository,
                         AddressRepository addressRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.addressRepository = addressRepository;
    }

    /**
     * Validation order mirrors CartService: cart state (empty), then the address the caller
     * asked for, then whether every line is still orderable - each check reports the more
     * fundamental problem first.
     */
    @Transactional
    public OrderDTO placeOrder(Long userId, Long addressId) {
        Cart cart = cartRepository.findByUserId(userId)
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new EmptyCartException("Cart is empty"));

        Address address = addressRepository.findByUserIdAndId(userId, addressId)
                .orElseThrow(() -> new AddressNotFoundException("Address not found: " + addressId));

        for (CartItem item : cart.getItems()) {
            if (!item.getFoodItem().isAvailable()) {
                throw new CartConflictException(
                        "Food item is not available: " + item.getFoodItem().getName());
            }
        }

        Order order = Order.builder()
                .user(cart.getUser())
                .restaurant(cart.getRestaurant())
                .address(address)
                .status(OrderStatus.PLACED)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            BigDecimal priceAtOrder = item.getFoodItem().getPrice();
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .foodItem(item.getFoodItem())
                    .quantity(item.getQuantity())
                    .priceAtOrder(priceAtOrder)
                    .build());
            total = total.add(priceAtOrder.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

        Order saved = orderRepository.save(order);

        cart.getItems().clear();
        cart.setRestaurant(null);
        cartRepository.save(cart);

        return OrderDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getUserOrders(Long userId, int page, int size) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PagedResponse.from(orders.map(OrderDTO::from));
    }

    @Transactional(readOnly = true)
    public OrderDetailDTO getOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findByUserIdAndId(userId, orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return OrderDetailDTO.from(order);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=OrderServiceTest` from `backend/`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/OrderService.java backend/src/test/java/com/foodrush/backend/service/OrderServiceTest.java
git commit -m "feat: add OrderService for checkout, order history, and order detail"
```

---

### Task 5: OrderController

**Files:**
- Create: `src/main/java/com/foodrush/backend/controller/OrderController.java`
- Test: `src/test/java/com/foodrush/backend/controller/OrderControllerTest.java` (mirrors `CartControllerTest.java`: `@WebMvcTest(OrderController.class)`, `@AutoConfigureMockMvc(addFilters = false)`, principal seeded manually)

**Interfaces:**
- Consumes: `OrderService.placeOrder/getUserOrders/getOrderById` (Task 4), `PlaceOrderRequest`/`OrderDTO`/`OrderDetailDTO`/`PagedResponse` (Task 2 + existing), `UserPrincipal` (existing).
- Produces: `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{orderId}` — final task, nothing downstream depends on this.

- [ ] **Step 1: Write the failing tests**

```java
package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.OrderItemDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.PlaceOrderRequest;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.exception.EmptyCartException;
import com.foodrush.backend.exception.OrderNotFoundException;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private OrderService orderService;

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

    private OrderDTO sampleOrder() {
        return new OrderDTO(50L, 1L, "Spice Route", new BigDecimal("120.00"),
                OrderStatus.PLACED, LocalDateTime.of(2026, 8, 5, 12, 0));
    }

    @Test
    void placeOrder_returns201WithOrderConfirmation() throws Exception {
        when(orderService.placeOrder(USER_ID, 9L)).thenReturn(sampleOrder());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(9L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.totalAmount").value(120.00))
                .andExpect(jsonPath("$.status").value("PLACED"));

        verify(orderService).placeOrder(USER_ID, 9L);
    }

    @Test
    void placeOrder_returns400_whenAddressIdMissing() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_whenCartIsEmpty() throws Exception {
        when(orderService.placeOrder(USER_ID, 9L)).thenThrow(new EmptyCartException("Cart is empty"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(9L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns404_whenAddressDoesNotBelongToUser() throws Exception {
        when(orderService.placeOrder(USER_ID, 42L))
                .thenThrow(new AddressNotFoundException("Address not found: 42"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaceOrderRequest(42L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrders_returns200WithPagedHistory() throws Exception {
        PagedResponse<OrderDTO> page = new PagedResponse<>(List.of(sampleOrder()), 1, 1, 0);
        when(orderService.getUserOrders(USER_ID, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/orders").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getOrders_defaultsToPageZeroSizeTen() throws Exception {
        when(orderService.getUserOrders(USER_ID, 0, 10))
                .thenReturn(new PagedResponse<>(List.of(), 0, 0, 0));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());

        verify(orderService).getUserOrders(USER_ID, 0, 10);
    }

    @Test
    void getOrder_returns200WithDetail() throws Exception {
        OrderDetailDTO detail = new OrderDetailDTO(50L, "Spice Route", new BigDecimal("120.00"),
                OrderStatus.PLACED, LocalDateTime.of(2026, 8, 5, 12, 0),
                new AddressDTO(9L, "Home", "221B Baker Street", "Mumbai", "400001"),
                List.of(new OrderItemDTO(10L, "Samosa", 2, new BigDecimal("60.00"), new BigDecimal("120.00"))));
        when(orderService.getOrderById(USER_ID, 50L)).thenReturn(detail);

        mockMvc.perform(get("/api/orders/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Spice Route"))
                .andExpect(jsonPath("$.deliveryAddress.city").value("Mumbai"))
                .andExpect(jsonPath("$.items[0].foodItemName").value("Samosa"));
    }

    @Test
    void getOrder_returns404_whenOrderBelongsToAnotherUser() throws Exception {
        when(orderService.getOrderById(eq(USER_ID), eq(99L)))
                .thenThrow(new OrderNotFoundException("Order not found: 99"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=OrderControllerTest` from `backend/`
Expected: FAIL — `OrderController` does not exist yet

- [ ] **Step 3: Write OrderController**

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.OrderDTO;
import com.foodrush.backend.dto.OrderDetailDTO;
import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.PlaceOrderRequest;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDTO> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(principal.getId(), request.addressId()));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderDTO>> getOrders(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getUserOrders(principal.getId(), page, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailDTO> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(principal.getId(), orderId));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=OrderControllerTest` from `backend/`
Expected: PASS (7 tests)

- [ ] **Step 5: Run the full backend test suite**

Run: `./mvnw -q test` from `backend/`
Expected: BUILD SUCCESS, no regressions (note: `contextLoads`/`BackendApplicationTests` needs a real `.env`-backed DB per existing memory note — a bare run may fail that one unrelated test; all new unit/slice tests must pass regardless)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/controller/OrderController.java backend/src/test/java/com/foodrush/backend/controller/OrderControllerTest.java
git commit -m "feat: add OrderController for checkout, order history, and order detail"
```

---

## Self-Review Notes

- **Spec coverage:** `placeOrder` (Task 4), `getUserOrders`/`getOrderById` (Task 4), `POST/GET /api/orders`, `GET /api/orders/{orderId}` (Task 5), `OrderStatus` enum (already existed, unchanged), all 5 DTOs from the spec (Task 2) — covered. Rate limiting / logging filters mentioned nowhere in task 7's own scope (that's task 16) — correctly not included here.
- **Ownership status codes:** deliberately 404 not 403 for foreign resources — see Global Constraints.
- **Type consistency:** `OrderService` methods, `OrderRepository` methods, and DTO `from()` names cross-checked against controller/test usage above.
