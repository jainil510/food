package com.foodrush.backend.security;

import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProbeController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void authEndpoints_arePublic() throws Exception {
        mockMvc.perform(get("/api/auth/probe")).andExpect(status().isOk());
    }

    @Test
    void authEndpoints_doNotRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/probe")).andExpect(status().isOk());
    }

    @Test
    void restaurantBrowsing_isPublic() throws Exception {
        mockMvc.perform(get("/api/restaurants/probe")).andExpect(status().isOk());
    }

    @Test
    void foodItemsEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/food-items/probe")).andExpect(status().isOk());
    }

    @Test
    void categoriesEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/categories/probe")).andExpect(status().isOk());
    }

    @Test
    void cartEndpoint_rejectsUnauthenticatedRequest_with401() throws Exception {
        mockMvc.perform(get("/api/cart/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void cartEndpoint_allowsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/cart/probe").with(csrf())).andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_rejectsUnauthenticatedRequest_with401() throws Exception {
        mockMvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpoint_rejectsNonAdminUser_with403() throws Exception {
        mockMvc.perform(get("/api/admin/probe").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoint_allowsAdminUser() throws Exception {
        mockMvc.perform(get("/api/admin/probe").with(csrf())).andExpect(status().isOk());
    }

    @Test
    void jwtAuthenticationFilter_isWiredIntoChain_andAuthenticatesRealBearerToken() throws Exception {
        User user = User.builder()
                .id(9L)
                .name("Alan Turing")
                .email("alan@foodrush.com")
                .password("hashed")
                .role(Role.USER)
                .build();
        when(jwtUtil.validateToken("a-real-looking-token")).thenReturn(true);
        when(jwtUtil.extractUsername("a-real-looking-token")).thenReturn("alan@foodrush.com");
        when(userDetailsService.loadUserByUsername("alan@foodrush.com")).thenReturn(new UserPrincipal(user));

        mockMvc.perform(get("/api/cart/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer a-real-looking-token")
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
