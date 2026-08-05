# Task 8: User Profile Management APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give an authenticated user endpoints to view their profile, update their name/phone, and change their password.

**Architecture:** Follows the existing Task 6 (Address APIs) shape: record DTOs with static `from(...)` factories, a constructor-injected service, `@AuthenticationPrincipal UserPrincipal` in the controller, one dedicated exception mapped in `GlobalExceptionHandler`. No new architectural pattern, no security config changes (`/api/users/**` is already `authenticated()`).

**Tech Stack:** Spring Boot 4.1.0, Java 21, Spring Security (`PasswordEncoder`/BCrypt already configured), JPA, JUnit 5 + Mockito + AssertJ (unit tests), `@WebMvcTest` + MockMvc (controller tests).

## Global Constraints

- Design spec: `docs/superpowers/specs/2026-08-05-task8-user-profile-apis-design.md` — follow it exactly; do not add email-change support, account deletion, or JWT revocation (explicitly out of scope).
- `UpdateProfileRequest`/`ChangePasswordRequest` validation messages and regex must match the spec: phone `^$|^[0-9]{10}$`, new password `@Size(min = 8)`.
- Before adding any `@RequestMapping`/`@GetMapping`/etc. path, grep `backend/src/test/java/com/foodrush/backend/security/ProbeController.java` for collisions — confirmed clean for `/api/users/me` and `/api/users/me/password` as of this plan's writing (only `/api/users/probe`-style paths would collide, and none exist under `/api/users/**` today).
- Full-suite verification (final step of Task 2) MUST export `.env` first: `cd backend && set -a && . ../.env && set +a && ./mvnw test` — a bare `./mvnw test` fails `BackendApplicationTests.contextLoads` on unresolved `${DB_USERNAME}`, which is environment-only, not a real regression.

---

### Task 1: UserService — profile read/update, password change

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/dto/UserDTO.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/UpdateProfileRequest.java`
- Create: `backend/src/main/java/com/foodrush/backend/dto/ChangePasswordRequest.java`
- Create: `backend/src/main/java/com/foodrush/backend/exception/InvalidCurrentPasswordException.java`
- Modify: `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/foodrush/backend/service/UserService.java`
- Test: `backend/src/test/java/com/foodrush/backend/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `com.foodrush.backend.entity.User` (existing — `id`, `name`, `email`, `password`, `phone`, `role`, `createdAt`, all with Lombok getters/setters and a `@Builder`); `com.foodrush.backend.repository.UserRepository` (existing — `findById(Long)` via `JpaRepository`, `save(User)`); Spring's `org.springframework.security.crypto.password.PasswordEncoder` bean (already configured in `SecurityConfig`, BCrypt strength 10).
- Produces: `UserDTO(Long id, String name, String email, String phone, LocalDateTime createdAt)` with static `UserDTO.from(User)`; `UpdateProfileRequest(String name, String phone)`; `ChangePasswordRequest(String oldPassword, String newPassword)`; `InvalidCurrentPasswordException extends RuntimeException`; `UserService` with `getProfile(Long userId)`, `updateProfile(Long userId, UpdateProfileRequest request)`, `changePassword(Long userId, ChangePasswordRequest request)` — all consumed by Task 2's `UserController`.

- [ ] **Step 1: Create the DTOs**

`backend/src/main/java/com/foodrush/backend/dto/UserDTO.java`:

```java
package com.foodrush.backend.dto;

import com.foodrush.backend.entity.User;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {

    public static UserDTO from(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getCreatedAt());
    }
}
```

`backend/src/main/java/com/foodrush/backend/dto/UpdateProfileRequest.java`:

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        String name,

        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone must be a 10-digit number")
        String phone
) {
}
```

`backend/src/main/java/com/foodrush/backend/dto/ChangePasswordRequest.java`:

```java
package com.foodrush.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String oldPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        String newPassword
) {
}
```

- [ ] **Step 2: Create the exception**

`backend/src/main/java/com/foodrush/backend/exception/InvalidCurrentPasswordException.java`:

```java
package com.foodrush.backend.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Wire the exception into `GlobalExceptionHandler`**

In `backend/src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java`, add this handler method (placed next to `handleBadCredentials`, both being auth-failure 401s):

```java
    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(InvalidCurrentPasswordException ex,
                                                                       HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }
```

No new imports needed — `HttpServletRequest`, `HttpStatus`, `ResponseEntity` are already imported in this file.

- [ ] **Step 4: Write the failing tests for `UserService`**

`backend/src/test/java/com/foodrush/backend/service/UserServiceTest.java`:

```java
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
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=UserServiceTest`
Expected: FAIL/compile error — `UserService` does not exist yet.

- [ ] **Step 6: Implement `UserService`**

`backend/src/main/java/com/foodrush/backend/service/UserService.java`:

```java
package com.foodrush.backend.service;

import com.foodrush.backend.dto.ChangePasswordRequest;
import com.foodrush.backend.dto.UpdateProfileRequest;
import com.foodrush.backend.dto.UserDTO;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.InvalidCurrentPasswordException;
import com.foodrush.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserDTO getProfile(Long userId) {
        return UserDTO.from(requireUser(userId));
    }

    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        user.setName(request.name());
        user.setPhone(request.phone());
        return UserDTO.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=UserServiceTest`
Expected: PASS, 7 tests green.

- [ ] **Step 8: Commit**

```bash
cd backend
git add src/main/java/com/foodrush/backend/dto/UserDTO.java \
        src/main/java/com/foodrush/backend/dto/UpdateProfileRequest.java \
        src/main/java/com/foodrush/backend/dto/ChangePasswordRequest.java \
        src/main/java/com/foodrush/backend/exception/InvalidCurrentPasswordException.java \
        src/main/java/com/foodrush/backend/exception/GlobalExceptionHandler.java \
        src/main/java/com/foodrush/backend/service/UserService.java \
        src/test/java/com/foodrush/backend/service/UserServiceTest.java
git commit -m "feat: add UserService for profile view/update and password change"
```

---

### Task 2: UserController — HTTP layer for `/api/users/me`

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/controller/UserController.java`
- Test: `backend/src/test/java/com/foodrush/backend/controller/UserControllerTest.java`

**Interfaces:**
- Consumes: `UserService.getProfile(Long)`, `UserService.updateProfile(Long, UpdateProfileRequest)`, `UserService.changePassword(Long, ChangePasswordRequest)` (Task 1); `com.foodrush.backend.security.UserPrincipal` (existing — `getId()`, `getName()`, `getRole()`); `com.foodrush.backend.entity.User`/`Role` (existing, for test principal setup).
- Produces: HTTP endpoints `GET /api/users/me`, `PUT /api/users/me`, `PUT /api/users/me/password` — no later task depends on these directly (terminal for this feature).

- [ ] **Step 1: Write the failing controller tests**

`backend/src/test/java/com/foodrush/backend/controller/UserControllerTest.java`:

```java
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
```

Note: `ObjectMapper` needs `.findAndRegisterModules()` here (unlike `AddressControllerTest`'s bare `new ObjectMapper()`) because `UserDTO` serializes a `LocalDateTime` field and the plain mapper has no `JavaTimeModule` registered.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=UserControllerTest`
Expected: FAIL/compile error — `UserController` does not exist yet.

- [ ] **Step 3: Implement `UserController`**

`backend/src/main/java/com/foodrush/backend/controller/UserController.java`:

```java
package com.foodrush.backend.controller;

import com.foodrush.backend.dto.ChangePasswordRequest;
import com.foodrush.backend.dto.UpdateProfileRequest;
import com.foodrush.backend.dto.UserDTO;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserDTO> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getProfile(principal.getId()));
    }

    @PutMapping
    public ResponseEntity<UserDTO> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.getId(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=UserControllerTest`
Expected: PASS, 7 tests green.

- [ ] **Step 5: Commit**

```bash
cd backend
git add src/main/java/com/foodrush/backend/controller/UserController.java \
        src/test/java/com/foodrush/backend/controller/UserControllerTest.java
git commit -m "feat: add UserController for GET/PUT /api/users/me and password change"
```

- [ ] **Step 6: Run the full suite with `.env` exported (context-load + regression check)**

Run: `cd backend && set -a && . ../.env && set +a && ./mvnw test`
Expected: PASS — all prior tests plus the new `UserServiceTest`/`UserControllerTest` green, and `BackendApplicationTests.contextLoads` succeeds (confirms no `ProbeController` mapping collision and no Spring Data derived-query errors).

- [ ] **Step 7: Update Task Master status**

Run: `task-master set-status --id=8 --status=done` (or the equivalent `task-master-ai` MCP `set_task_status` call) — do this only after Step 6 passes.
