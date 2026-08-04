package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.AddressRequest;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.AddressService;
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

@WebMvcTest(AddressController.class)
@AutoConfigureMockMvc(addFilters = false)
class AddressControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AddressService addressService;

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

    private AddressDTO sampleAddress() {
        return new AddressDTO(1L, "Home", "221B Baker Street", "Mumbai", "400001");
    }

    @Test
    void getAddresses_returns200WithAddressList() throws Exception {
        when(addressService.getUserAddresses(USER_ID)).thenReturn(List.of(sampleAddress()));

        mockMvc.perform(get("/api/users/me/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].label").value("Home"))
                .andExpect(jsonPath("$[0].fullAddress").value("221B Baker Street"))
                .andExpect(jsonPath("$[0].city").value("Mumbai"))
                .andExpect(jsonPath("$[0].pincode").value("400001"));
    }

    @Test
    void getAddress_returns200WithSingleAddress() throws Exception {
        when(addressService.getAddressById(USER_ID, 1L)).thenReturn(sampleAddress());

        mockMvc.perform(get("/api/users/me/addresses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getAddress_returns404_whenNotOwnedByUser() throws Exception {
        when(addressService.getAddressById(USER_ID, 42L))
                .thenThrow(new AddressNotFoundException("Address not found: 42"));

        mockMvc.perform(get("/api/users/me/addresses/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAddress_returns201WithCreatedAddress() throws Exception {
        AddressRequest request = new AddressRequest("Home", "221B Baker Street", "Mumbai", "400001");
        when(addressService.createAddress(eq(USER_ID), any(AddressRequest.class))).thenReturn(sampleAddress());

        mockMvc.perform(post("/api/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Home"));

        verify(addressService).createAddress(eq(USER_ID), any(AddressRequest.class));
    }

    @Test
    void createAddress_returns400_whenFullAddressBlank() throws Exception {
        AddressRequest request = new AddressRequest("Home", "", "Mumbai", "400001");

        mockMvc.perform(post("/api/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns400_whenCityBlank() throws Exception {
        AddressRequest request = new AddressRequest("Home", "221B Baker Street", "", "400001");

        mockMvc.perform(post("/api/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns400_whenPincodeIsNotSixDigits() throws Exception {
        AddressRequest request = new AddressRequest("Home", "221B Baker Street", "Mumbai", "4001");

        mockMvc.perform(post("/api/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns400_whenPincodeContainsLetters() throws Exception {
        AddressRequest request = new AddressRequest("Home", "221B Baker Street", "Mumbai", "40000A");

        mockMvc.perform(post("/api/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_returns200WithUpdatedAddress() throws Exception {
        AddressRequest request = new AddressRequest("Office", "12 MG Road", "Pune", "411001");
        AddressDTO updated = new AddressDTO(1L, "Office", "12 MG Road", "Pune", "411001");
        when(addressService.updateAddress(eq(USER_ID), eq(1L), any(AddressRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/me/addresses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Pune"));
    }

    @Test
    void updateAddress_returns400_whenPincodeInvalid() throws Exception {
        AddressRequest request = new AddressRequest("Office", "12 MG Road", "Pune", "4110A1");

        mockMvc.perform(put("/api/users/me/addresses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_returns404_whenNotOwnedByUser() throws Exception {
        AddressRequest request = new AddressRequest("Office", "12 MG Road", "Pune", "411001");
        when(addressService.updateAddress(eq(USER_ID), eq(42L), any(AddressRequest.class)))
                .thenThrow(new AddressNotFoundException("Address not found: 42"));

        mockMvc.perform(put("/api/users/me/addresses/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAddress_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/me/addresses/1"))
                .andExpect(status().isNoContent());

        verify(addressService).deleteAddress(USER_ID, 1L);
    }

    @Test
    void deleteAddress_returns404_whenNotOwnedByUser() throws Exception {
        org.mockito.Mockito.doThrow(new AddressNotFoundException("Address not found: 42"))
                .when(addressService).deleteAddress(USER_ID, 42L);

        mockMvc.perform(delete("/api/users/me/addresses/42"))
                .andExpect(status().isNotFound());
    }
}
