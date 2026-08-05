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
