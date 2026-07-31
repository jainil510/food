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
