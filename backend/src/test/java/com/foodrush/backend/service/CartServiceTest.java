package com.foodrush.backend.service;

import com.foodrush.backend.dto.AddToCartRequest;
import com.foodrush.backend.dto.CartDTO;
import com.foodrush.backend.entity.Cart;
import com.foodrush.backend.entity.CartItem;
import com.foodrush.backend.entity.FoodItem;
import com.foodrush.backend.entity.Restaurant;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.CartConflictException;
import com.foodrush.backend.exception.CartItemNotFoundException;
import com.foodrush.backend.exception.FoodItemNotFoundException;
import com.foodrush.backend.repository.CartRepository;
import com.foodrush.backend.repository.FoodItemRepository;
import com.foodrush.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
