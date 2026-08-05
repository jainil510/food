package com.foodrush.backend.service;

import com.foodrush.backend.dto.ChangePasswordRequest;
import com.foodrush.backend.dto.UpdateProfileRequest;
import com.foodrush.backend.dto.UserDTO;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.InvalidCurrentPasswordException;
import com.foodrush.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    static User user(Long id) {
        return User.builder()
                .id(id).name("Asha").email("asha@foodrush.com")
                .password("hashed-old-password").phone("9876543210")
                .role(Role.USER).createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }

    @Test
    void getProfile_returnsUserWithoutPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));

        UserDTO result = userService.getProfile(USER_ID);

        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.name()).isEqualTo("Asha");
        assertThat(result.email()).isEqualTo("asha@foodrush.com");
        assertThat(result.phone()).isEqualTo("9876543210");
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    @Test
    void getProfile_throwsIllegalState_whenAuthenticatedUserIsGone() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated user not found: " + USER_ID);
    }

    @Test
    void updateProfile_updatesNameAndPhoneAndReturnsUpdated() {
        User existing = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDTO result = userService.updateProfile(USER_ID, new UpdateProfileRequest("Asha Verma", "9123456780"));

        assertThat(result.name()).isEqualTo("Asha Verma");
        assertThat(result.phone()).isEqualTo("9123456780");
        assertThat(result.email()).isEqualTo("asha@foodrush.com");
    }

    @Test
    void updateProfile_throwsIllegalState_whenAuthenticatedUserIsGone() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(USER_ID,
                new UpdateProfileRequest("Asha Verma", "9123456780")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated user not found: " + USER_ID);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_updatesToEncodedNewPassword_whenOldPasswordMatches() {
        User existing = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("old-password", "hashed-old-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("hashed-new-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword(USER_ID, new ChangePasswordRequest("old-password", "new-password-123"));

        verify(userRepository).save(existing);
        assertThat(existing.getPassword()).isEqualTo("hashed-new-password");
    }

    @Test
    void changePassword_throwsInvalidCurrentPassword_whenOldPasswordDoesNotMatch() {
        User existing = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong-password", "hashed-old-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(USER_ID,
                new ChangePasswordRequest("wrong-password", "new-password-123")))
                .isInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_throwsIllegalState_whenAuthenticatedUserIsGone() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(USER_ID,
                new ChangePasswordRequest("old-password", "new-password-123")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated user not found: " + USER_ID);
    }
}
