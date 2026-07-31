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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public void register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(Role.USER)
                .build();
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);
        return new AuthResponse(token, principal.getId(), principal.getName(), principal.getRole());
    }
}
