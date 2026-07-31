package com.foodrush.backend.service;

import com.foodrush.backend.dto.AuthResponse;
import com.foodrush.backend.dto.LoginRequest;
import com.foodrush.backend.dto.RegisterRequest;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.DuplicateEmailException;
import com.foodrush.backend.repository.UserRepository;
import com.foodrush.backend.security.JwtUtil;
import com.foodrush.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtUtil);
    }

    @Test
    void register_savesUserWithHashedPasswordAndUserRole_whenEmailNotTaken() {
        RegisterRequest request = new RegisterRequest("Grace Hopper", "grace@foodrush.com", "plaintext123", "9876543210");
        when(userRepository.findByEmail("grace@foodrush.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plaintext123")).thenReturn("hashed-password");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Grace Hopper");
        assertThat(saved.getEmail()).isEqualTo("grace@foodrush.com");
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.getPhone()).isEqualTo("9876543210");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void register_throwsDuplicateEmailException_whenEmailAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("Grace Hopper", "grace@foodrush.com", "plaintext123", "9876543210");
        User existing = User.builder().id(1L).email("grace@foodrush.com").build();
        when(userRepository.findByEmail("grace@foodrush.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsAuthResponse_onSuccessfulAuthentication() {
        LoginRequest request = new LoginRequest("ada@foodrush.com", "correct-password");
        User user = User.builder()
                .id(3L)
                .name("Ada Lovelace")
                .email("ada@foodrush.com")
                .password("hashed")
                .role(Role.ADMIN)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        Authentication authentication = new TestingAuthenticationToken(principal, null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken(principal)).thenReturn("signed-jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isEqualTo(new AuthResponse("signed-jwt-token", 3L, "Ada Lovelace", "ADMIN"));
    }

    @Test
    void login_propagatesBadCredentialsException_onAuthenticationFailure() {
        LoginRequest request = new LoginRequest("ada@foodrush.com", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtUtil, never()).generateToken(any());
    }
}
