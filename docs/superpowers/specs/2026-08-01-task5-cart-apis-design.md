# Task 5: Shopping Cart APIs and Business Logic — Design

**Date:** 2026-08-01
**Task Master ID:** 5 (depends on Task 4, which is done)
**Status:** approved, pending implementation plan

## Goal

Give an authenticated user a persistent cart: add food items, adjust quantities, remove
items, clear the cart, and read it back with an auto-calculated total — subject to FR-14,
the rule that a cart holds items from exactly one restaurant at a time.

## Scope

In scope: `CartRepository`, `CartItemRepository`, `CartService`, `CartController`, three
DTOs, two exceptions, and a fix to `FoodItemService.deleteFoodItem` that Task 4 explicitly
deferred to this task.

Out of scope: checkout and order placement (Task 7), the cart UI (Task 11), and any
`orders`/`addresses` work. Cart contents are not validated against restaurant opening hours
or stock levels — neither concept exists in the schema.

## Architecture

Service-layer orchestration with anemic entities, matching every other service in the
codebase (`RestaurantService`, `FoodItemService`). `CartService` owns the invariants;
`Cart` and `CartItem` stay plain JPA entities; DTOs are records with static `from(...)`
factories, as `FoodItemDTO` already is.

Two approaches were rejected. A rich domain model (invariants enforced on the `Cart`
aggregate) is better OO but would make `Cart` the only entity in the project shaped that
way, and would push availability rules — which belong to `FoodItem` — into the cart
aggregate. Split query/command services are unjustified ceremony for five endpoints.

Two cross-cutting decisions:

- **`GET /api/cart` never writes.** A user with no cart row gets an empty representation
  (`restaurantId: null`, `items: []`, `total: 0.00`); no row is inserted. Only the mutating
  paths call `getOrCreateCart`. A GET that creates rows is surprising and would create a
  junk row for every user who merely opens the cart page.
- **Totals are computed, never stored.** The `carts` table has no total column, and a
  stored total drifts the moment an admin edits a price. The total is summed at read time
  in `BigDecimal`.

## Existing constraints this design must respect

- `/api/cart/**` is already `authenticated()` in `SecurityConfig` — no security config
  changes are needed, and unauthenticated access already returns 401.
- `carts.user_id` is UNIQUE, so a user has at most one cart. `carts.restaurant_id` is
  nullable, which is what makes the "empty cart has no restaurant" state representable.
- `cart_items` has **no** unique constraint on `(cart_id, food_item_id)`. Duplicate
  prevention is therefore the service's job, not the database's.
- `Cart.items` is `@OneToMany(cascade = ALL, orphanRemoval = true)`, so removing a line from
  the collection deletes the row.
- The test-only `ProbeController` maps `GET /api/cart/probe`. This design must not
  introduce a `GET /api/cart/{pathVariable}` route, which would ambiguously collide with it
  under `@SpringBootTest`.

## HTTP contract

Every endpoint returns the same full `CartDTO`, so the client always holds a fresh total and
never needs a follow-up GET.

| Method | Path | Request body | Success |
|---|---|---|---|
| GET | `/api/cart` | — | 200 `CartDTO` |
| POST | `/api/cart/items` | `AddToCartRequest` | 200 `CartDTO` |
| PUT | `/api/cart/items/{cartItemId}` | `UpdateCartItemRequest` | 200 `CartDTO` |
| DELETE | `/api/cart/items/{cartItemId}` | — | 200 `CartDTO` |
| DELETE | `/api/cart` | — | 200 `CartDTO` (empty) |

`POST` returns 200 rather than 201 because the resource described by the response is the
cart, which already existed conceptually — not a newly created addressable resource; there
is no `GET /api/cart/items/{id}` to point a `Location` header at. The `DELETE`s return
200 with a body rather than 204 for the same reason every endpoint does: the caller needs
the recalculated total.

`DELETE /api/cart` is not in PRD section 11.3, which lists only four cart endpoints. It is
specified in `tasks.json` and is required by FR-14: the 409 conflict message tells the user
to "clear cart first", which is only actionable if a clear endpoint exists.

### Error responses

All errors use the existing `ErrorResponse` shape from `GlobalExceptionHandler`.

| Condition | Status | Exception |
|---|---|---|
| Food item does not exist | 404 | `FoodItemNotFoundException` (existing) |
| `cartItemId` does not exist, **or belongs to another user** | 404 | `CartItemNotFoundException` (new) |
| Item is from a different restaurant than the cart | 409 | `CartConflictException` (new) |
| Item is not available | 409 | `CartConflictException` (new) |
| Request body fails bean validation | 400 | `MethodArgumentNotValidException` (existing) |
| No/invalid JWT | 401 | handled by `SecurityConfig` entry point |

Cross-user access returns 404, not 403, so that another user's `cartItemId` is
indistinguishable from a nonexistent one and existence is not leaked.

One `CartConflictException` class carries both 409 messages rather than two nearly
identical classes: the HTTP contract is identical and only the message differs.

## Data transfer objects

```java
CartDTO(Long restaurantId, String restaurantName, List<CartItemDTO> items, BigDecimal total)

CartItemDTO(Long cartItemId, Long foodItemId, String name, BigDecimal price,
            Integer quantity, BigDecimal subtotal, boolean isAvailable)

AddToCartRequest(@NotNull Long foodItemId, @NotNull @Min(1) Integer quantity)

UpdateCartItemRequest(@NotNull @Min(0) Integer quantity)
```

`@Min(1)` on add and `@Min(0)` on update is intentional, not an inconsistency: quantity 0 on
update is the documented "remove this line" path, while adding zero of something is
meaningless.

`CartItemDTO.isAvailable` mirrors `FoodItemDTO`'s field name and JSON property exactly.

## Business rules

These are the behaviors `CartService` guarantees. Each is named here so the implementation
plan and its tests can refer to them by number.

1. **Single restaurant.** On add, the cart's `restaurant` must be null or equal to the food
   item's restaurant. Otherwise 409 with the message
   `"Cart can only contain items from one restaurant. Clear cart first?"`. When it is null,
   adding sets it.
2. **Merge, don't duplicate.** Adding a food item already in the cart increments the
   existing line's quantity by the requested amount instead of creating a second line.
3. **Quantity 0 removes.** `PUT` with quantity 0 deletes the line rather than storing a
   zero-quantity row.
4. **Emptying resets the restaurant.** Whenever the last line leaves the cart — via rule 3,
   via `DELETE /api/cart/items/{id}`, or via `DELETE /api/cart` — `cart.restaurant` is set
   back to null. Without this the user stays locked to the old restaurant after emptying
   their cart, which contradicts the intent of FR-14. This rule is not stated in
   `tasks.json`.
5. **Unavailable items cannot be added.** Adding a food item whose `isAvailable` is false
   is a 409.
6. **Unavailable lines already in the cart are flagged, not purged.** A line whose food item
   became unavailable after it was added (Task 4 soft-deletes items with order history by
   setting `isAvailable = false`) is still returned, with `isAvailable: false`, and its
   subtotal is **excluded from `total`**. The line is never silently deleted: a GET must not
   mutate state, and the user deserves an explanation for a vanishing item. Task 7 checkout
   is expected to reject a cart containing such lines.
7. **Ownership.** Every operation taking a `cartItemId` verifies the line belongs to the
   requesting user's cart, throwing `CartItemNotFoundException` if not.
8. **Total.** `total` is the `BigDecimal` sum of each available line's
   `price × quantity`, scale 2. `subtotal` is reported per line, including for unavailable
   lines, even though those do not contribute to `total`.

**Validation order on add.** Rules 5 and 1 can both apply to one request — an unavailable
item from a different restaurant. `addItemToCart` checks in a fixed order: the food item
exists (404), then it is available (409, unavailable message), then the restaurant matches
(409, single-restaurant message). Availability is checked first because it is a property of
the item alone, while the restaurant conflict depends on cart state the user can clear.

## Components

**`CartRepository extends JpaRepository<Cart, Long>`**
`Optional<Cart> findByUserId(Long userId)`, with an `@EntityGraph` fetching `restaurant`,
`items`, and `items.foodItem` to avoid N+1 selects when building `CartDTO`.

**`CartItemRepository extends JpaRepository<CartItem, Long>`**
`void deleteByFoodItemId(Long foodItemId)` for the Task 4 fix below. Used solely by
`FoodItemService` — `CartService` never depends on it.

`findByCartId` from the task description is deliberately omitted: `Cart.items` already
provides the same data through the existing `@OneToMany`. `findByCartIdAndFoodItemId`,
originally planned for rule 2, is also omitted: the entity graph on `CartRepository.findByUserId`
already loads every line, so the merge lookup and the ownership check both scan the
already-fetched `cart.getItems()` in memory instead of issuing a second query.

**`CartService`** — `getCart`, `addItemToCart`, `updateCartItem`, `removeCartItem`,
`clearCart`, all returning `CartDTO`. Read path is `@Transactional(readOnly = true)`;
mutations are `@Transactional`. Depends on `CartRepository`, `FoodItemRepository`, and
`UserRepository` (to attach the owner when creating a cart) — not `CartItemRepository`.

**`CartController`** — `@RequestMapping("/api/cart")`, five thin methods that resolve the
user via `@AuthenticationPrincipal UserPrincipal` and delegate. The task description
suggests `SecurityContextHolder`; `@AuthenticationPrincipal` reads the same value but is
injectable and far easier to test.

**Exceptions** — `CartItemNotFoundException` and `CartConflictException`, each a
`RuntimeException` in the `exception` package, each registered in `GlobalExceptionHandler`
alongside the existing handlers.

## Change to existing code

Task 4 documented, and deferred to this task, that hard-deleting a food item sitting in
someone's cart raises `DataIntegrityViolationException` → 409, because
`cart_items.food_item_id` is a foreign key. Task 5 owns that table, so it fixes it:
`FoodItemService` gains a `CartItemRepository` dependency and calls
`deleteByFoodItemId(id)` immediately before `foodItemRepository.delete(foodItem)`.

The soft-delete branch (items with order history) is untouched — those items stay in carts
and surface via rule 6.

**Post-launch addendum:** the final whole-branch review found that purging cart lines this
way bypasses `CartService.saveAndConvert`, where rule 4 (an empty cart holds no restaurant)
is normally enforced — a cart whose only line was removed here stayed pinned to the old
restaurant. `FoodItemService` gained a further `CartRepository` dependency and a call to
`CartRepository.clearRestaurantFromEmptyCarts()` (a bulk `@Modifying` update) immediately
after the purge, restoring rule 4 on this path too.

## Testing

**`CartServiceTest`** — Mockito unit tests, one or more per business rule: add to empty cart
sets the restaurant; add from the same restaurant succeeds; add from a different restaurant
throws `CartConflictException`; re-adding increments instead of duplicating; adding an
unavailable item throws; update to 0 removes; removing the last line nulls the restaurant;
clear empties and nulls the restaurant; total is accurate across multiple lines and
quantities; total excludes unavailable lines; a cart item owned by another user throws
`CartItemNotFoundException`.

**`CartControllerTest`** — `@WebMvcTest(CartController.class)` with
`@AutoConfigureMockMvc(addFilters = false)` and a `@MockitoBean CartService`, following
`FoodItemAdminControllerTest`. Covers the five status codes, the JSON shape of `CartDTO`,
bean-validation 400s (missing `foodItemId`, quantity below minimum), and the 404/409
mappings.

**`FoodItemServiceTest`** — one new case proving cart lines are purged before a hard delete,
and confirmation that the existing soft-delete case still does not touch cart lines.

The 401-for-unauthenticated requirement from the task's test strategy is already covered by
`SecurityConfigTest.cartEndpoint_rejectsUnauthenticatedRequest_with401`, which exercises the
real filter chain. It is not duplicated here.

Note that running the suite requires the `.env` values to be exported; a bare `./mvnw test`
fails `contextLoads` on an unresolved `${DB_USERNAME}`.

## Open questions

None.
