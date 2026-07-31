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

@WebMvcTest(RestaurantAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void createRestaurant_returns400_whenNameExceedsMaxLength() throws Exception {
        String tooLongName = "A".repeat(151);
        RestaurantRequest request = new RestaurantRequest(tooLongName, null, "12 MG Road", null, null, null);

        mockMvc.perform(post("/api/admin/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRestaurant_returns409_whenActiveOrdersExist() throws Exception {
        org.mockito.Mockito.doThrow(new RestaurantHasActiveOrdersException("Cannot delete restaurant with active orders: 1"))
                .when(restaurantService).deleteRestaurant(1L);

        mockMvc.perform(delete("/api/admin/restaurants/1"))
                .andExpect(status().isConflict());
    }
}
