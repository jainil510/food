# Task 3: Restaurant and Category Management APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Task 3 from `.taskmaster/tasks/tasks.json` — public restaurant discovery APIs (list/paginate, get by id, search by name, filter by cuisine), the category list API, and admin CRUD for restaurants, with the delete path guarded against restaurants that still have non-terminal orders.

**Architecture:** Same layered style as Tasks 1–2: `controller -> service -> repository`, manual DTO mapping via static `from(...)` factory methods (no ModelMapper — none is on the classpath and Task 2 didn't introduce one), Bean Validation on request DTOs, and exception translation through the existing `GlobalExceptionHandler` (`@RestControllerAdvice`). Public read endpoints live under `/api/restaurants/**` and `/api/categories/**` (already/newly permit-all in `SecurityConfig`); admin writes live under `/api/admin/restaurants/**`, which `SecurityConfig` already gates to `ROLE_ADMIN` via a path matcher — so no `@PreAuthorize`/method security is introduced (the codebase has none; don't add `@EnableMethodSecurity` speculatively).

**Tech Stack:** Spring Boot 4.1.0, Spring Data JPA (derived query methods only — no custom `@Query`), Bean Validation (`jakarta.validation`), JUnit 5 + Mockito + AssertJ for unit tests, `@WebMvcTest` for controller tests (matching `SecurityConfigTest`'s existing usage of `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`).

## Global Constraints

- Reuse existing entities as-is — **do not modify** `Restaurant.java`, `Category.java`, `FoodItem.java`, `Order.java`, `OrderStatus.java` (they were validated against the Flyway schema in Task 1). No new bidirectional JPA relationships/cascades are added to entities; restaurant-delete cascade to food items is done explicitly via a repository delete-derived-query, not `@OneToMany(cascade=...)`.
- No repository-level DB tests: this codebase has zero `@DataJpaTest`/H2 usage (even `UserRepository.findByEmail` from Task 2 is untested at the repository layer) and no H2 dependency is cached locally. Follow the existing convention — trust Spring Data's derived-query generation, and cover behavior via service-layer unit tests with mocked repositories.
- No `@PreAuthorize`/`@EnableMethodSecurity` — access control stays entirely in `SecurityConfig`'s `requestMatchers(...)` chain, matching Tasks 1–2.
- Pagination is plain `int page, int size` request params turned into `PageRequest.of(page, size)` in the service layer — no `Pageable` Spring MVC argument resolver (not used anywhere yet in this codebase).
- Paginated response shape (exact, from task spec): `{content, totalPages, totalElements, currentPage}`.
- `GET /api/categories` must be **public** (it powers the public restaurant filter dropdown per task 3 details) — this requires adding `/api/categories/**` to `SecurityConfig`'s permit-all matchers, which is currently not covered and would otherwise fall through to `.anyRequest().authenticated()`.
- Admin restaurant delete: block with 409 if the restaurant has any order not in a terminal state (`DELIVERED`, `CANCELLED`); otherwise cascade-delete its food items, then delete the restaurant.
- Rating: optional, `BigDecimal`, must be in `[0.0, 5.0]` when present. Image URL: optional, must start with `http://` or `https://` when present (empty string allowed, since a nullable text form field commonly round-trips as `""`).
- New minimal `FoodItemRepository` and `OrderRepository` are being introduced ahead of Tasks 4 and 7 because Task 3's own delete-safety requirement needs them — keep them to only the methods this task needs; Tasks 4/7 will extend them.

---

## File Structure

**Backend — new files:**
- `repository/CategoryRepository.java` — plain `JpaRepository<Category, Long>`
- `dto/CategoryDTO.java`
- `service/CategoryService.java`
- `controller/CategoryController.java` — `GET /api/categories`
- `dto/PagedResponse.java` — generic paginated envelope
- `dto/RestaurantDTO.java`
- `repository/RestaurantRepository.java` — `findByNameContainingIgnoreCase`, `findByCuisineType`
- `exception/RestaurantNotFoundException.java`
- `service/RestaurantService.java` — public read methods first, admin write methods added later in this same file
- `controller/RestaurantController.java` — public endpoints
- `dto/RestaurantRequest.java` — admin create/update payload
- `repository/FoodItemRepository.java` — minimal: `deleteByRestaurantId`
- `repository/OrderRepository.java` — minimal: `existsByRestaurantIdAndStatusNotIn`
- `exception/RestaurantHasActiveOrdersException.java`
- `controller/RestaurantAdminController.java` — admin endpoints

**Backend — modified files:**
- `security/SecurityConfig.java` — add `/api/categories/**` to the public matcher
- `src/test/java/.../security/ProbeController.java` — add a `/api/categories/probe` route
- `src/test/java/.../security/SecurityConfigTest.java` — add a test asserting the categories probe is public
- `exception/GlobalExceptionHandler.java` — add handlers for `RestaurantNotFoundException` (404) and `RestaurantHasActiveOrdersException` (409)

**Backend — new test files:**
- `service/CategoryServiceTest.java`
- `controller/CategoryControllerTest.java`
- `service/RestaurantServiceTest.java`
- `controller/RestaurantControllerTest.java`
- `controller/RestaurantAdminControllerTest.java`
- `exception/GlobalExceptionHandlerTest.java` — extended with two new test methods (existing file, not replaced)

---

### Task 1: Category read API + make `/api/categories` public

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/repository/CategoryRepository.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/CategoryDTO.java`
- Create: `backend/src/main/java/com/foodrush/backend/service/CategoryService.java`
- Create: `backend/src/main/java/com/foodrush/backend/controller/CategoryController.java`
- Modify: `backend/src/main/java/com/foodrush/backend/security/SecurityConfig.java:50-54`
- Modify: `backend/src/test/java/com/foodrush/backend/security/ProbeController.java`
- Modify: `backend/src/test/java/com/foodrush/backend/security/SecurityConfigTest.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/CategoryServiceTest.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/CategoryControllerTest.java`

**Interfaces:**
- Produces: `CategoryDTO(Long id, String name)` with static `CategoryDTO.from(Category)`; `CategoryService.getAllCategories(): List<CategoryDTO>`; `CategoryRepository extends JpaRepository<Category, Long>` (no custom methods).

- [ ] **Step 1: Write the failing service test**

Create `backend/src/test/java/com/foodrush/backend/service/CategoryServiceTest.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.CategoryDTO;
import com.foodrush.backend.entity.Category;
import com.foodrush.backend.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void getAllCategories_returnsAllCategoriesMappedToDTOs() {
        Category northIndian = Category.builder().id(1L).name("North Indian").build();
        Category desserts = Category.builder().id(2L).name("Desserts").build();
        when(categoryRepository.findAll()).thenReturn(List.of(northIndian, desserts));

        List<CategoryDTO> result = categoryService.getAllCategories();

        assertThat(result).containsExactly(
                new CategoryDTO(1L, "North Indian"),
                new CategoryDTO(2L, "Desserts"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile (no `CategoryRepository`/`CategoryService`/`CategoryDTO` exist yet)**

Run: `cd backend && ./mvnw test -Dtest=CategoryServiceTest`
Expected: BUILD FAILURE — compilation error, symbols not found.

- [ ] **Step 3: Create the production classes**

Create `backend/src/main/java/com/foodrush/backend/repository/CategoryRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
```

Create `backend/src/main/java/com/foodrush/backend/dto/CategoryDTO.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Category;

public record CategoryDTO(Long id, String name) {

    public static CategoryDTO from(Category category) {
        return new CategoryDTO(category.getId(), category.getName());
    }
}
```

Create `backend/src/main/java/com/foodrush/backend/service/CategoryService.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.CategoryDTO;
import com.foodrush.backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryDTO::from)
                .toList();
    }
}
```

- [ ] **Step 4: Run the service test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CategoryServiceTest`
Expected: BUILD SUCCESS, 1 test passed.

- [ ] **Step 5: Write the failing controller test**

Create `backend/src/test/java/com/foodrush/backend/controller/CategoryControllerTest.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.CategoryDTO;
import com.foodrush.backend.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void getAllCategories_returns200WithCategoryList() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of(
                new CategoryDTO(1L, "North Indian"),
                new CategoryDTO(2L, "Desserts")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("North Indian"))
                .andExpect(jsonPath("$[1].name").value("Desserts"));
    }
}
```

- [ ] **Step 6: Run it to verify it fails (no `CategoryController` yet)**

Run: `cd backend && ./mvnw test -Dtest=CategoryControllerTest`
Expected: BUILD FAILURE — compilation error.

- [ ] **Step 7: Create the controller**

Create `backend/src/main/java/com/foodrush/backend/controller/CategoryController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.CategoryDTO;
import com.foodrush.backend.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }
}
```

- [ ] **Step 8: Run the controller test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CategoryControllerTest`
Expected: BUILD SUCCESS, 1 test passed.

- [ ] **Step 9: Make `/api/categories` public in `SecurityConfig`, with a probe + security test**

In `backend/src/main/java/com/foodrush/backend/security/SecurityConfig.java`, change:

```java
                        .requestMatchers("/api/restaurants/**").permitAll()
```

to:

```java
                        .requestMatchers("/api/restaurants/**", "/api/categories/**").permitAll()
```

In `backend/src/test/java/com/foodrush/backend/security/ProbeController.java`, add (alongside the other probes):

```java
    @GetMapping("/api/categories/probe")
    String categoriesProbe() {
        return "ok";
    }
```

In `backend/src/test/java/com/foodrush/backend/security/SecurityConfigTest.java`, add a new test method next to `restaurantBrowsing_isPublic`:

```java
    @Test
    void categoriesEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/categories/probe")).andExpect(status().isOk());
    }
```

- [ ] **Step 10: Run the security test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=SecurityConfigTest`
Expected: BUILD SUCCESS, all `SecurityConfigTest` tests (including the new one) pass.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/repository/CategoryRepository.java \
        backend/src/main/java/com/foodrush/backend/dto/CategoryDTO.java \
        backend/src/main/java/com/foodrush/backend/service/CategoryService.java \
        backend/src/main/java/com/foodrush/backend/controller/CategoryController.java \
        backend/src/main/java/com/foodrush/backend/security/SecurityConfig.java \
        backend/src/test/java/com/foodrush/backend/security/ProbeController.java \
        backend/src/test/java/com/foodrush/backend/security/SecurityConfigTest.java \
        backend/src/test/java/com/foodrush/backend/service/CategoryServiceTest.java \
        backend/src/test/java/com/foodrush/backend/controller/CategoryControllerTest.java
git commit -m "feat: add public category listing API"
```

---

### Task 2: `PagedResponse`, `RestaurantDTO`, `RestaurantRepository`

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/dto/PagedResponse.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/RestaurantDTO.java`
- Create: `backend/src/main/java/com/foodrush/backend/repository/RestaurantRepository.java`

**Interfaces:**
- Produces: `PagedResponse<T>(List<T> content, int totalPages, long totalElements, int currentPage)` with static `PagedResponse.from(Page<T>)`; `RestaurantDTO(Long id, String name, String description, String address, String cuisineType, BigDecimal rating, String imageUrl, LocalDateTime createdAt)` with static `RestaurantDTO.from(Restaurant)`; `RestaurantRepository extends JpaRepository<Restaurant, Long>` with `Page<Restaurant> findByNameContainingIgnoreCase(String name, Pageable pageable)` and `Page<Restaurant> findByCuisineType(String cuisineType, Pageable pageable)`.
- These are pure data classes/interfaces with no business logic of their own, so this task has no dedicated unit test — they're exercised through `RestaurantServiceTest` in Task 3. Verify correctness here by compiling.

- [ ] **Step 1: Create `PagedResponse`**

Create `backend/src/main/java/com/foodrush/backend/dto/PagedResponse.java`:

```java
package com.foodrush.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(List<T> content, int totalPages, long totalElements, int currentPage) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getNumber());
    }
}
```

- [ ] **Step 2: Create `RestaurantDTO`**

Create `backend/src/main/java/com/foodrush/backend/dto/RestaurantDTO.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Restaurant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RestaurantDTO(
        Long id,
        String name,
        String description,
        String address,
        String cuisineType,
        BigDecimal rating,
        String imageUrl,
        LocalDateTime createdAt
) {

    public static RestaurantDTO from(Restaurant restaurant) {
        return new RestaurantDTO(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getAddress(),
                restaurant.getCuisineType(),
                restaurant.getRating(),
                restaurant.getImageUrl(),
                restaurant.getCreatedAt());
    }
}
```

- [ ] **Step 3: Create `RestaurantRepository`**

Create `backend/src/main/java/com/foodrush/backend/repository/RestaurantRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Page<Restaurant> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Restaurant> findByCuisineType(String cuisineType, Pageable pageable);
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/dto/PagedResponse.java \
        backend/src/main/java/com/foodrush/backend/dto/RestaurantDTO.java \
        backend/src/main/java/com/foodrush/backend/repository/RestaurantRepository.java
git commit -m "feat: add restaurant DTOs and repository for discovery APIs"
```

---

### Task 3: `RestaurantService` — public read methods

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/exception/RestaurantNotFoundException.java`
- Create: `backend/src/main/java/com/foodrush/backend/service/RestaurantService.java` (public-read methods only — admin methods added in Task 6)
- Modify: `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/RestaurantServiceTest.java`
- Test: `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java` (extend existing file)

**Interfaces:**
- Consumes: `RestaurantRepository` (Task 2), `RestaurantDTO.from(Restaurant)`, `PagedResponse.from(Page<T>)` (Task 2).
- Produces: `RestaurantService.getAllRestaurants(int page, int size): PagedResponse<RestaurantDTO>`, `getRestaurantById(Long id): RestaurantDTO` (throws `RestaurantNotFoundException`), `searchRestaurants(String query, int page, int size): PagedResponse<RestaurantDTO>`, `filterByCuisine(String cuisineType, int page, int size): PagedResponse<RestaurantDTO>`. `RestaurantNotFoundException extends RuntimeException`.

- [ ] **Step 1: Write the failing service tests**

Create `backend/src/test/java/com/foodrush/backend/service/RestaurantServiceTest.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private OrderRepository orderRepository;

    private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository, foodItemRepository, orderRepository);
    }

    private Restaurant sampleRestaurant() {
        return Restaurant.builder()
                .id(1L)
                .name("Spice Route")
                .description("Authentic North Indian")
                .address("12 MG Road")
                .cuisineType("North Indian")
                .rating(new BigDecimal("4.5"))
                .imageUrl("https://example.com/spice-route.jpg")
                .build();
    }

    @Test
    void getAllRestaurants_returnsPagedResponseFromRepository() {
        Page<Restaurant> page = new PageImpl<>(java.util.List.of(sampleRestaurant()), PageRequest.of(0, 10), 1);
        when(restaurantRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<RestaurantDTO> result = restaurantService.getAllRestaurants(0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Spice Route");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.currentPage()).isEqualTo(0);
    }

    @Test
    void getRestaurantById_returnsDTO_whenFound() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(sampleRestaurant()));

        RestaurantDTO result = restaurantService.getRestaurantById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Spice Route");
    }

    @Test
    void getRestaurantById_throwsRestaurantNotFoundException_whenMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getRestaurantById(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void searchRestaurants_delegatesToNameSearchRepositoryMethod() {
        Page<Restaurant> page = new PageImpl<>(java.util.List.of(sampleRestaurant()), PageRequest.of(0, 10), 1);
        when(restaurantRepository.findByNameContainingIgnoreCase(eq("spice"), any(Pageable.class))).thenReturn(page);

        PagedResponse<RestaurantDTO> result = restaurantService.searchRestaurants("spice", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Spice Route");
    }

    @Test
    void filterByCuisine_delegatesToCuisineRepositoryMethod() {
        Page<Restaurant> page = new PageImpl<>(java.util.List.of(sampleRestaurant()), PageRequest.of(0, 10), 1);
        when(restaurantRepository.findByCuisineType(eq("North Indian"), any(Pageable.class))).thenReturn(page);

        PagedResponse<RestaurantDTO> result = restaurantService.filterByCuisine("North Indian", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).cuisineType()).isEqualTo("North Indian");
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile** (`RestaurantService`, `RestaurantNotFoundException`, `FoodItemRepository`, `OrderRepository` don't exist yet)

Run: `cd backend && ./mvnw test -Dtest=RestaurantServiceTest`
Expected: BUILD FAILURE — compilation error.

> Note: this test file references `FoodItemRepository` and `OrderRepository` in the constructor even though `RestaurantService` in this task only implements read methods — that's intentional so the constructor shape doesn't change again in Task 6. Task 5 creates those two repository interfaces before this test can compile; if executing tasks strictly in order, come back and re-run this test after Task 5 lands, or do Task 5 first. **Recommended order: do Task 5 immediately before this task's Step 3** if running sequentially, since `RestaurantService`'s constructor needs all three repositories to exist to compile.

- [ ] **Step 3: Create `RestaurantNotFoundException`**

Create `backend/src/main/java/com/foodrush/backend/exception/RestaurantNotFoundException.java`:

```java
package com.foodrush.backend.exception;

public class RestaurantNotFoundException extends RuntimeException {

    public RestaurantNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Create `RestaurantService` (public read methods)**

Create `backend/src/main/java/com/foodrush/backend/service/RestaurantService.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.OrderRepository;
import com.foodrush.backend.repository.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final FoodItemRepository foodItemRepository;
    private final OrderRepository orderRepository;

    public RestaurantService(RestaurantRepository restaurantRepository,
                              FoodItemRepository foodItemRepository,
                              OrderRepository orderRepository) {
        this.restaurantRepository = restaurantRepository;
        this.foodItemRepository = foodItemRepository;
        this.orderRepository = orderRepository;
    }

    public PagedResponse<RestaurantDTO> getAllRestaurants(int page, int size) {
        Page<Restaurant> result = restaurantRepository.findAll(PageRequest.of(page, size));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public RestaurantDTO getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        return RestaurantDTO.from(restaurant);
    }

    public PagedResponse<RestaurantDTO> searchRestaurants(String query, int page, int size) {
        Page<Restaurant> result = restaurantRepository.findByNameContainingIgnoreCase(query, PageRequest.of(page, size));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }

    public PagedResponse<RestaurantDTO> filterByCuisine(String cuisineType, int page, int size) {
        Page<Restaurant> result = restaurantRepository.findByCuisineType(cuisineType, PageRequest.of(page, size));
        return PagedResponse.from(result.map(RestaurantDTO::from));
    }
}
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=RestaurantServiceTest`
Expected: BUILD SUCCESS, 5 tests passed.

- [ ] **Step 6: Add exception-handler tests to the existing `GlobalExceptionHandlerTest`**

Add these two test methods inside the existing class in `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java` (next to `handleDuplicateEmail_returns409WithMessage`):

```java
    @Test
    void handleRestaurantNotFound_returns404WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/restaurants/99");
        RestaurantNotFoundException ex = new RestaurantNotFoundException("Restaurant not found: 99");

        ResponseEntity<ErrorResponse> response = handler.handleRestaurantNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Restaurant not found: 99");
    }
```

(The second handler test, for `RestaurantHasActiveOrdersException`, is added in Task 6 once that exception exists.)

- [ ] **Step 7: Run it to verify it fails to compile** (`handler.handleRestaurantNotFound` doesn't exist yet)

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD FAILURE — compilation error.

- [ ] **Step 8: Add the handler to `GlobalExceptionHandler`**

In `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`, add this method (next to `handleDuplicateEmail`):

```java
    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRestaurantNotFound(RestaurantNotFoundException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }
```

Add the import: `import com.foodrush.backend.exception.RestaurantNotFoundException;` — actually this class is already in package `com.foodrush.backend.exception`, so no import is needed (same package as `GlobalExceptionHandler` and `RestaurantNotFoundException`).

- [ ] **Step 9: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD SUCCESS, all tests (including the new one) pass.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/exception/RestaurantNotFoundException.java \
        backend/src/main/java/com/foodrush/backend/service/RestaurantService.java \
        backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java \
        backend/src/test/java/com/foodrush/backend/service/RestaurantServiceTest.java \
        backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: add restaurant public read service methods with 404 handling"
```

---

### Task 4: `RestaurantController` — public endpoints

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/controller/RestaurantController.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/RestaurantControllerTest.java`

**Interfaces:**
- Consumes: `RestaurantService.getAllRestaurants/getRestaurantById/searchRestaurants/filterByCuisine` (Task 3), `PagedResponse<RestaurantDTO>`, `RestaurantDTO` (Task 2).
- Produces: `GET /api/restaurants?cuisine=&page=&size=`, `GET /api/restaurants/{id}`, `GET /api/restaurants/search?query=&page=&size=`.

- [ ] **Step 1: Write the failing controller tests**

Create `backend/src/test/java/com/foodrush/backend/controller/RestaurantControllerTest.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RestaurantController.class)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    private RestaurantDTO sampleDTO() {
        return new RestaurantDTO(1L, "Spice Route", "Authentic North Indian", "12 MG Road",
                "North Indian", new BigDecimal("4.5"), "https://example.com/spice-route.jpg",
                LocalDateTime.of(2026, 7, 1, 10, 0));
    }

    @Test
    void getRestaurants_returnsPagedResponse_whenNoCuisineFilter() throws Exception {
        when(restaurantService.getAllRestaurants(0, 10))
                .thenReturn(new PagedResponse<>(java.util.List.of(sampleDTO()), 1, 1, 0));

        mockMvc.perform(get("/api/restaurants").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Spice Route"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getRestaurants_delegatesToCuisineFilter_whenCuisineParamPresent() throws Exception {
        when(restaurantService.filterByCuisine(eq("Italian"), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(java.util.List.of(), 0, 0, 0));

        mockMvc.perform(get("/api/restaurants").param("cuisine", "Italian"))
                .andExpect(status().isOk());
    }

    @Test
    void getRestaurantById_returns200WithRestaurant_whenFound() throws Exception {
        when(restaurantService.getRestaurantById(1L)).thenReturn(sampleDTO());

        mockMvc.perform(get("/api/restaurants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spice Route"));
    }

    @Test
    void getRestaurantById_returns404_whenNotFound() throws Exception {
        when(restaurantService.getRestaurantById(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/restaurants/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchRestaurants_returnsPagedResponse() throws Exception {
        when(restaurantService.searchRestaurants(eq("spice"), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(java.util.List.of(sampleDTO()), 1, 1, 0));

        mockMvc.perform(get("/api/restaurants/search").param("query", "spice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Spice Route"));
    }
}
```

> Note: no `@Import(GlobalExceptionHandler.class)` is needed here — `@WebMvcTest`'s slice already auto-detects `@RestControllerAdvice` beans (`GlobalExceptionHandler` is one). Adding an explicit `@Import` on top of that would register it a second time and throw a `ConflictingBeanDefinitionException` at context startup.

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd backend && ./mvnw test -Dtest=RestaurantControllerTest`
Expected: BUILD FAILURE — compilation error (no `RestaurantController`).

- [ ] **Step 3: Create `RestaurantController`**

Create `backend/src/main/java/com/foodrush/backend/controller/RestaurantController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.PagedResponse;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.service.RestaurantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RestaurantDTO>> getRestaurants(
            @RequestParam(required = false) String cuisine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<RestaurantDTO> response = (cuisine == null || cuisine.isBlank())
                ? restaurantService.getAllRestaurants(page, size)
                : restaurantService.filterByCuisine(cuisine, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDTO> getRestaurantById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getRestaurantById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<RestaurantDTO>> searchRestaurants(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(query, page, size));
    }
}
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=RestaurantControllerTest`
Expected: BUILD SUCCESS, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/controller/RestaurantController.java \
        backend/src/test/java/com/foodrush/backend/controller/RestaurantControllerTest.java
git commit -m "feat: add public restaurant discovery endpoints"
```

---

### Task 5: Admin request DTO + minimal `FoodItemRepository`/`OrderRepository` + `RestaurantHasActiveOrdersException`

> **Do this task before Task 3's Step 3 if executing strictly in order** — `RestaurantService`'s constructor (Task 3) depends on `FoodItemRepository` and `OrderRepository` existing to compile.

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/dto/RestaurantRequest.java`
- Create: `backend/src/main/java/com/foodrush/backend/repository/FoodItemRepository.java`
- Create: `backend/src/main/java/com/foodrush/backend/repository/OrderRepository.java`
- Create: `backend/src/main/java/com/foodrush/backend/exception/RestaurantHasActiveOrdersException.java`

**Interfaces:**
- Produces: `RestaurantRequest(String name, String description, String address, String cuisineType, BigDecimal rating, String imageUrl)` with Bean Validation; `FoodItemRepository extends JpaRepository<FoodItem, Long>` with `long deleteByRestaurantId(Long restaurantId)`; `OrderRepository extends JpaRepository<Order, Long>` with `boolean existsByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<OrderStatus> excludedStatuses)`; `RestaurantHasActiveOrdersException extends RuntimeException`.
- No dedicated unit test for this task — these are exercised through `RestaurantServiceTest` (Task 3, admin methods added in Task 6). Verify by compiling.

- [ ] **Step 1: Create `RestaurantRequest`**

Create `backend/src/main/java/com/foodrush/backend/dto/RestaurantRequest.java`:

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record RestaurantRequest(
        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotBlank(message = "Address is required")
        String address,

        String cuisineType,

        @DecimalMin(value = "0.0", message = "Rating must be at least 0")
        @DecimalMax(value = "5.0", message = "Rating must be at most 5")
        BigDecimal rating,

        @Pattern(regexp = "^$|^https?://.+", message = "Image URL must be a valid http(s) URL")
        String imageUrl
) {
}
```

- [ ] **Step 2: Create `FoodItemRepository`**

Create `backend/src/main/java/com/foodrush/backend/repository/FoodItemRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    long deleteByRestaurantId(Long restaurantId);
}
```

- [ ] **Step 3: Create `OrderRepository`**

Create `backend/src/main/java/com/foodrush/backend/repository/OrderRepository.java`:

```java
package com.foodrush.backend.repository;

import com.foodrush.backend.entity.Order;
import com.foodrush.backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<OrderStatus> excludedStatuses);
}
```

- [ ] **Step 4: Create `RestaurantHasActiveOrdersException`**

Create `backend/src/main/java/com/foodrush/backend/exception/RestaurantHasActiveOrdersException.java`:

```java
package com.foodrush.backend.exception;

public class RestaurantHasActiveOrdersException extends RuntimeException {

    public RestaurantHasActiveOrdersException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/dto/RestaurantRequest.java \
        backend/src/main/java/com/foodrush/backend/repository/FoodItemRepository.java \
        backend/src/main/java/com/foodrush/backend/repository/OrderRepository.java \
        backend/src/main/java/com/foodrush/backend/exception/RestaurantHasActiveOrdersException.java
git commit -m "feat: add admin restaurant request DTO and delete-safety repositories"
```

---

### Task 6: `RestaurantService` — admin write methods

**Files:**
- Modify: `backend/src/main/java/com/foodrush/backend/service/RestaurantService.java`
- Modify: `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/foodrush/backend/service/RestaurantServiceTest.java`
- Modify: `backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `RestaurantRequest` (Task 5), `FoodItemRepository.deleteByRestaurantId` (Task 5), `OrderRepository.existsByRestaurantIdAndStatusNotIn` (Task 5), `RestaurantHasActiveOrdersException` (Task 5).
- Produces: `RestaurantService.createRestaurant(RestaurantRequest): RestaurantDTO`, `updateRestaurant(Long id, RestaurantRequest): RestaurantDTO` (throws `RestaurantNotFoundException`), `deleteRestaurant(Long id): void` (throws `RestaurantNotFoundException`, `RestaurantHasActiveOrdersException`).

- [ ] **Step 1: Add the failing admin-method tests to `RestaurantServiceTest`**

Add these test methods to the existing `RestaurantServiceTest` class (add these imports too: `com.foodrush.backend.dto.RestaurantRequest`, `com.foodrush.backend.exception.RestaurantHasActiveOrdersException`, `org.mockito.ArgumentCaptor`, `static org.mockito.Mockito.never`, `static org.mockito.Mockito.verify`):

```java
    @Test
    void createRestaurant_savesAndReturnsRestaurant() {
        RestaurantRequest request = new RestaurantRequest(
                "Spice Route", "Authentic North Indian", "12 MG Road", "North Indian",
                new BigDecimal("4.5"), "https://example.com/spice-route.jpg");
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        RestaurantDTO result = restaurantService.createRestaurant(request);

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Spice Route");
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Spice Route");
    }

    @Test
    void updateRestaurant_updatesFieldsAndSaves_whenFound() {
        Restaurant existing = sampleRestaurant();
        RestaurantRequest request = new RestaurantRequest(
                "Spice Route Updated", "New description", "13 MG Road", "South Indian",
                new BigDecimal("4.8"), "https://example.com/updated.jpg");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantDTO result = restaurantService.updateRestaurant(1L, request);

        assertThat(result.name()).isEqualTo("Spice Route Updated");
        assertThat(result.cuisineType()).isEqualTo("South Indian");
        assertThat(result.rating()).isEqualByComparingTo("4.8");
    }

    @Test
    void updateRestaurant_throwsRestaurantNotFoundException_whenMissing() {
        RestaurantRequest request = new RestaurantRequest("X", null, "Y", null, null, null);
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.updateRestaurant(99L, request))
                .isInstanceOf(RestaurantNotFoundException.class);

        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void deleteRestaurant_deletesFoodItemsThenRestaurant_whenNoActiveOrders() {
        Restaurant existing = sampleRestaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByRestaurantIdAndStatusNotIn(eq(1L), any())).thenReturn(false);

        restaurantService.deleteRestaurant(1L);

        verify(foodItemRepository).deleteByRestaurantId(1L);
        verify(restaurantRepository).delete(existing);
    }

    @Test
    void deleteRestaurant_throwsRestaurantHasActiveOrdersException_whenActiveOrdersExist() {
        Restaurant existing = sampleRestaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByRestaurantIdAndStatusNotIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> restaurantService.deleteRestaurant(1L))
                .isInstanceOf(RestaurantHasActiveOrdersException.class);

        verify(foodItemRepository, never()).deleteByRestaurantId(any());
        verify(restaurantRepository, never()).delete(any());
    }

    @Test
    void deleteRestaurant_throwsRestaurantNotFoundException_whenMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.deleteRestaurant(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }
```

- [ ] **Step 2: Run it to verify it fails to compile** (admin methods don't exist on `RestaurantService` yet)

Run: `cd backend && ./mvnw test -Dtest=RestaurantServiceTest`
Expected: BUILD FAILURE — compilation error.

- [ ] **Step 3: Add the admin methods to `RestaurantService`**

In `backend/src/main/java/com/foodrush/backend/service/RestaurantService.java`, add these imports:

```java
import com.foodrush.backend.dto.RestaurantRequest;
import com.foodrush.backend.entity.OrderStatus;
import com.foodrush.backend.exception.RestaurantHasActiveOrdersException;

import java.util.List;
```

Add this field (next to the existing repository fields):

```java
    private static final List<OrderStatus> TERMINAL_ORDER_STATUSES = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
```

Add these methods (at the end of the class):

```java
    public RestaurantDTO createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.name())
                .description(request.description())
                .address(request.address())
                .cuisineType(request.cuisineType())
                .rating(request.rating())
                .imageUrl(request.imageUrl())
                .build();
        return RestaurantDTO.from(restaurantRepository.save(restaurant));
    }

    public RestaurantDTO updateRestaurant(Long id, RestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        restaurant.setName(request.name());
        restaurant.setDescription(request.description());
        restaurant.setAddress(request.address());
        restaurant.setCuisineType(request.cuisineType());
        restaurant.setRating(request.rating());
        restaurant.setImageUrl(request.imageUrl());
        return RestaurantDTO.from(restaurantRepository.save(restaurant));
    }

    public void deleteRestaurant(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        if (orderRepository.existsByRestaurantIdAndStatusNotIn(id, TERMINAL_ORDER_STATUSES)) {
            throw new RestaurantHasActiveOrdersException("Cannot delete restaurant with active orders: " + id);
        }
        foodItemRepository.deleteByRestaurantId(id);
        restaurantRepository.delete(restaurant);
    }
```

- [ ] **Step 4: Run the service tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=RestaurantServiceTest`
Expected: BUILD SUCCESS, 11 tests passed.

- [ ] **Step 5: Add the second exception-handler test to `GlobalExceptionHandlerTest`**

Add this test method to the existing class (add import `com.foodrush.backend.exception.RestaurantHasActiveOrdersException` — already same package, no import needed):

```java
    @Test
    void handleRestaurantHasActiveOrders_returns409WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/admin/restaurants/1");
        RestaurantHasActiveOrdersException ex =
                new RestaurantHasActiveOrdersException("Cannot delete restaurant with active orders: 1");

        ResponseEntity<ErrorResponse> response = handler.handleRestaurantHasActiveOrders(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Cannot delete restaurant with active orders: 1");
    }
```

- [ ] **Step 6: Run it to verify it fails to compile**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD FAILURE — compilation error (`handleRestaurantHasActiveOrders` doesn't exist).

- [ ] **Step 7: Add the handler to `GlobalExceptionHandler`**

Add this method (next to `handleRestaurantNotFound`):

```java
    @ExceptionHandler(RestaurantHasActiveOrdersException.class)
    public ResponseEntity<ErrorResponse> handleRestaurantHasActiveOrders(RestaurantHasActiveOrdersException ex,
                                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/service/RestaurantService.java \
        backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java \
        backend/src/test/java/com/foodrush/backend/service/RestaurantServiceTest.java \
        backend/src/test/java/com/foodrush/backend/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: add restaurant admin create/update/delete with active-order guard"
```

---

### Task 7: `RestaurantAdminController` — admin endpoints

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/controller/RestaurantAdminController.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/RestaurantAdminControllerTest.java`

**Interfaces:**
- Consumes: `RestaurantService.createRestaurant/updateRestaurant/deleteRestaurant` (Task 6), `RestaurantRequest` (Task 5).
- Produces: `POST /api/admin/restaurants`, `PUT /api/admin/restaurants/{id}`, `DELETE /api/admin/restaurants/{id}`.

- [ ] **Step 1: Write the failing controller tests**

Create `backend/src/test/java/com/foodrush/backend/controller/RestaurantAdminControllerTest.java`:

```java
package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.dto.RestaurantRequest;
import com.foodrush.backend.exception.RestaurantHasActiveOrdersException;
import com.foodrush.backend.exception.RestaurantNotFoundException;
import com.foodrush.backend.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RestaurantAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestaurantService restaurantService;

    private RestaurantDTO sampleDTO() {
        return new RestaurantDTO(1L, "Spice Route", "Authentic North Indian", "12 MG Road",
                "North Indian", new BigDecimal("4.5"), "https://example.com/spice-route.jpg",
                LocalDateTime.of(2026, 7, 1, 10, 0));
    }

    @Test
    void createRestaurant_returns201WithCreatedRestaurant() throws Exception {
        RestaurantRequest request = new RestaurantRequest("Spice Route", "Authentic North Indian",
                "12 MG Road", "North Indian", new BigDecimal("4.5"), "https://example.com/spice-route.jpg");
        when(restaurantService.createRestaurant(any(RestaurantRequest.class))).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/admin/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spice Route"));
    }

    @Test
    void createRestaurant_returns400_whenNameBlank() throws Exception {
        RestaurantRequest request = new RestaurantRequest("", null, "12 MG Road", null, null, null);

        mockMvc.perform(post("/api/admin/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRestaurant_returns200WithUpdatedRestaurant() throws Exception {
        RestaurantRequest request = new RestaurantRequest("Spice Route Updated", "New desc",
                "13 MG Road", "South Indian", new BigDecimal("4.8"), "https://example.com/updated.jpg");
        when(restaurantService.updateRestaurant(eq(1L), any(RestaurantRequest.class))).thenReturn(sampleDTO());

        mockMvc.perform(put("/api/admin/restaurants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateRestaurant_returns404_whenRestaurantMissing() throws Exception {
        RestaurantRequest request = new RestaurantRequest("Name", null, "Address", null, null, null);
        when(restaurantService.updateRestaurant(eq(99L), any(RestaurantRequest.class)))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(put("/api/admin/restaurants/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRestaurant_returns204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/admin/restaurants/1"))
                .andExpect(status().isNoContent());

        verify(restaurantService).deleteRestaurant(1L);
    }

    @Test
    void deleteRestaurant_returns409_whenActiveOrdersExist() throws Exception {
        org.mockito.Mockito.doThrow(new RestaurantHasActiveOrdersException("Cannot delete restaurant with active orders: 1"))
                .when(restaurantService).deleteRestaurant(1L);

        mockMvc.perform(delete("/api/admin/restaurants/1"))
                .andExpect(status().isConflict());
    }
}
```

> Note: same as `RestaurantControllerTest` — no `@Import(GlobalExceptionHandler.class)`; `@WebMvcTest` already auto-detects `@RestControllerAdvice` beans.

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `cd backend && ./mvnw test -Dtest=RestaurantAdminControllerTest`
Expected: BUILD FAILURE — compilation error (no `RestaurantAdminController`).

- [ ] **Step 3: Create `RestaurantAdminController`**

Create `backend/src/main/java/com/foodrush/backend/controller/RestaurantAdminController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.RestaurantDTO;
import com.foodrush.backend.dto.RestaurantRequest;
import com.foodrush.backend.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/restaurants")
public class RestaurantAdminController {

    private final RestaurantService restaurantService;

    public RestaurantAdminController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @PostMapping
    public ResponseEntity<RestaurantDTO> createRestaurant(@Valid @RequestBody RestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createRestaurant(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestaurantDTO> updateRestaurant(@PathVariable Long id,
                                                           @Valid @RequestBody RestaurantRequest request) {
        return ResponseEntity.ok(restaurantService.updateRestaurant(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable Long id) {
        restaurantService.deleteRestaurant(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=RestaurantAdminControllerTest`
Expected: BUILD SUCCESS, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/controller/RestaurantAdminController.java \
        backend/src/test/java/com/foodrush/backend/controller/RestaurantAdminControllerTest.java
git commit -m "feat: add admin restaurant CRUD endpoints"
```

---

### Task 8: Full verification and task status update

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS, all tests pass (existing Task 1/2 tests + all new tests from this plan).

- [ ] **Step 2: Run a full build**

Run: `cd backend && ./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS, jar produced under `backend/target/`.

- [ ] **Step 3: Mark Task 3 done in Task Master**

Run: `task-master set-status --id=3 --status=done` (from the `FoodRush` project root, or via the `task-master-ai` MCP tool `set_task_status` with `id: "3"`, `status: "done"` if working inside Claude Code).

- [ ] **Step 4: Commit any final cleanup**

```bash
git status
```

If anything is unstaged (e.g. stray build artifacts should already be gitignored — verify `target/` isn't tracked), stage and commit only the intended source changes. No separate commit is expected here if Steps 1–7 above already committed everything.
