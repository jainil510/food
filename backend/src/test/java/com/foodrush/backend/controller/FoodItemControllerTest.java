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
        when(foodItemService.getMenuByRestaurant(99L, null))
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
}
