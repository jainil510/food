package com.foodrush.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrush.backend.dto.ChangePasswordRequest;
import com.foodrush.backend.dto.UpdateProfileRequest;
import com.foodrush.backend.dto.UserDTO;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.InvalidCurrentPasswordException;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.UserService;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserService userService;

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

    private UserDTO sampleUser() {
        return new UserDTO(USER_ID, "Asha", "asha@foodrush.com", "9876543210",
                LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    @Test
    void getProfile_returns200WithUser() throws Exception {
        when(userService.getProfile(USER_ID)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.email").value("asha@foodrush.com"))
                .andExpect(jsonPath("$.phone").value("9876543210"));
    }

    @Test
    void updateProfile_returns200WithUpdatedUser() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Asha Verma", "9123456780");
        UserDTO updated = new UserDTO(USER_ID, "Asha Verma", "asha@foodrush.com", "9123456780",
                LocalDateTime.of(2026, 1, 1, 10, 0));
        when(userService.updateProfile(eq(USER_ID), any(UpdateProfileRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Verma"))
                .andExpect(jsonPath("$.phone").value("9123456780"));

        verify(userService).updateProfile(eq(USER_ID), any(UpdateProfileRequest.class));
    }

    @Test
    void updateProfile_returns400_whenNameBlank() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("", "9123456780");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_returns400_whenPhoneInvalid() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Asha Verma", "12345");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_returns400_whenNameTooLong() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("A".repeat(101), "9123456780");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returns204_onSuccess() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("old-password", "new-password-123");

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(eq(USER_ID), any(ChangePasswordRequest.class));
    }

    @Test
    void changePassword_returns400_whenNewPasswordTooShort() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("old-password", "short");

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returns401_whenOldPasswordWrong() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong-password", "new-password-123");
        doThrow(new InvalidCurrentPasswordException("Current password is incorrect"))
                .when(userService).changePassword(eq(USER_ID), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
