# Task 8: User Profile Management APIs — Design

**Date:** 2026-08-05
**Task Master ID:** 8 (depends on Task 2, which is done)
**Status:** approved, pending implementation plan

## Goal

Let an authenticated user view their own account profile, update their name/phone, and
change their password.

## Scope

In scope: `UserService`, `UserController`, three DTOs (`UserDTO`, `UpdateProfileRequest`,
`ChangePasswordRequest`), one new exception (`InvalidCurrentPasswordException`), and the
corresponding unit/controller tests.

Out of scope: email changes (the update endpoint's body is deliberately `{name, phone}`
only, per the task spec's "email change optional/restricted for simplicity"), account
deletion, and JWT revocation on password change — this app has no token-blacklist
mechanism anywhere, so an existing token remains valid until its normal 24h expiry after a
password change. That's a pre-existing, accepted property of the stateless-JWT design, not
something Task 8 introduces or should fix.

## Architecture

Follows the Task 6 (Address APIs) shape exactly, since it's the most recent sibling
feature: record DTOs with static `from(...)` factories, a constructor-injected service,
`@AuthenticationPrincipal UserPrincipal` in the controller, and one dedicated exception
mapped in `GlobalExceptionHandler` — no new architectural pattern introduced.

**Endpoints** (already `authenticated()` under `/api/users/**` in `SecurityConfig` — no
security config changes needed):
- `GET /api/users/me` → `UserDTO`
- `PUT /api/users/me` → body `UpdateProfileRequest`, returns updated `UserDTO`
- `PUT /api/users/me/password` → body `ChangePasswordRequest`, `204 No Content`

**DTOs:**
- `UserDTO(Long id, String name, String email, String phone, LocalDateTime createdAt)`
- `UpdateProfileRequest(String name, String phone)` — `name` `@NotBlank`; `phone` reuses
  `RegisterRequest`'s `@Pattern(regexp = "^$|^[0-9]{10}$")` (blank or exactly 10 digits)
- `ChangePasswordRequest(String oldPassword, String newPassword)` — `oldPassword`
  `@NotBlank`; `newPassword` `@NotBlank @Size(min = 8)`

**Service (`UserService`)**, constructor-injected with `UserRepository` and
`PasswordEncoder`:
- `getProfile(Long userId)` → load user, map to `UserDTO`
- `updateProfile(Long userId, UpdateProfileRequest request)` → load user, set
  `name`/`phone`, save, return `UserDTO`
- `changePassword(Long userId, ChangePasswordRequest request)`:
  - load user
  - `passwordEncoder.matches(request.oldPassword(), user.getPassword())` — if false,
    throw `InvalidCurrentPasswordException`
  - else `user.setPassword(passwordEncoder.encode(request.newPassword()))`, save

All three methods resolve the user via `userRepository.findById(userId)`, throwing
`IllegalStateException("Authenticated user not found: " + userId)` on absence — matching
`AddressService.createAddress`'s existing pattern for the "authenticated principal's user
row is gone" case, which should be unreachable in practice.

**Exception handling:** new `InvalidCurrentPasswordException extends RuntimeException`,
mapped in `GlobalExceptionHandler` to `401 Unauthorized` with message "Current password is
incorrect" — kept distinct from login's deliberately generic `BadCredentialsException`
message, since there's no email-enumeration concern in an already-authenticated context.

**Controller (`UserController`):** same shape as `AddressController` — takes
`@AuthenticationPrincipal UserPrincipal principal`, delegates to `UserService`, `@Valid
@RequestBody` on both PUT endpoints.

## Testing

Mirrors `AddressServiceTest` / `AddressControllerTest`:

- `UserServiceTest` (Mockito unit tests): get profile; update profile happy path; change
  password happy path; change password with wrong old password throws
  `InvalidCurrentPasswordException` and never saves.
- `UserControllerTest` (`@WebMvcTest(UserController.class)`, mocked `UserService`): 200 for
  get/update/password-change; 400 for blank name, invalid phone, short new password; 401
  when service throws `InvalidCurrentPasswordException`.

No `@DataJpaTest`/persistence-layer tests are needed — `UserRepository` gains no new
derived-query methods (it already has `findById` via `JpaRepository` and `findByEmail`,
unused here).
