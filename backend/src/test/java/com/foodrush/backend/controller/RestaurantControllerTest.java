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
