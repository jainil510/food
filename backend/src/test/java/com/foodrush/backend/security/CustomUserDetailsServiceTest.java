package com.foodrush.backend.security;

import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_returnsUserDetailsWithEmailPasswordAndRoleAuthority_whenUserExists() {
        service = new CustomUserDetailsService(userRepository);
        User user = User.builder()
                .id(1L)
                .name("Ada Lovelace")
                .email("ada@foodrush.com")
                .password("hashed-password")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmail("ada@foodrush.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("ada@foodrush.com");

        assertThat(result.getUsername()).isEqualTo("ada@foodrush.com");
        assertThat(result.getPassword()).isEqualTo("hashed-password");
        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) result).getId()).isEqualTo(1L);
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenNoUserWithThatEmail() {
        service = new CustomUserDetailsService(userRepository);
        when(userRepository.findByEmail("missing@foodrush.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@foodrush.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
