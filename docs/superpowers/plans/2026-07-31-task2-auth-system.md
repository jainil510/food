# Task 2: Authentication System (JWT + Spring Security) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Execution override (user-directed for this task):** Do not run the standard uniform TDD/review cycle on every sub-task. Sub-tasks are labeled SIMPLE or COMPLEX below:
> - **SIMPLE** sub-tasks are implemented together in one batch, sanity-checked (compiles, no obvious errors) only.
> - **COMPLEX** sub-tasks each get the full plan → implement → test → review cycle, one at a time.
> - A single combined review + full test run happens once at the end, not per sub-task.

**Goal:** Implement Task 2 from `.taskmaster/tasks/tasks.json` — user registration, login, JWT issuance/validation, and Spring Security role-based access control, plus the minimal frontend auth plumbing (AuthContext, Login/Register pages, route guards) that later frontend tasks (10–15) build on.

**Architecture:** Stateless JWT auth. `JwtAuthenticationFilter` runs once per request, extracts/validates the bearer token, and populates `SecurityContextHolder`. `SecurityConfig` wires the filter into Spring Security's chain and declares the three access tiers (public / authenticated / admin). `AuthService` owns register/login business logic; `AuthController` is a thin HTTP wrapper. Frontend mirrors this with `AuthContext` holding token+user state in `localStorage`, an axios instance-level 401 interceptor for auto-logout, and `ProtectedRoute`/`AdminRoute` guarding routes.

**Tech Stack:** Spring Boot 4.1.0, Spring Security 6.x, jjwt 0.12.7 (already on the classpath), BCrypt (`spring-boot-starter-security` default), Lombok · React 19, react-router-dom 7, axios 1, Context API.

## Global Constraints

- JWT: HMAC-SHA256, 24h expiry, claims carry `userId` and `role`. Secret comes from env var `JWT_SECRET` (add to `.env`/`.env.example`), never hardcoded.
- Password hashing: `BCryptPasswordEncoder` strength 10.
- Access tiers (exact paths, from PRD §11 / task spec):
  - Public: `/api/auth/**`, `/api/restaurants/**`, `/api/restaurants/{id}/menu`
  - Authenticated: `/api/cart/**`, `/api/orders/**`, `/api/users/**`
  - Admin-only: `/api/admin/**`
- CSRF disabled (stateless API), session policy `STATELESS`.
- Login response shape: `{token, userId, name, role}`. Register returns a success message only (no auto-login).
- Existing entities/enums to reuse, not redefine: `User` (`backend/src/main/java/com/foodrush/backend/entity/User.java`), `Role` enum (`USER`/`ADMIN`).
- Frontend scope for *this* task is the minimal plumbing only (AuthContext, Login.jsx, Register.jsx, ProtectedRoute, AdminRoute, routing wire-up). Navbar, "returnUrl" redirect polish, and richer field-level validation UX belong to Task 13 — don't build them early.
- Cart single-restaurant rule (FR-14) is out of scope here (Task 5).

---

## File Structure

**Backend — new files:**
- `security/JwtUtil.java` — token generate/validate/extract
- `security/JwtAuthenticationFilter.java` — per-request bearer token → SecurityContext
- `security/CustomUserDetailsService.java` — loads `User` by email for Spring Security
- `security/SecurityConfig.java` — `SecurityFilterChain`, `PasswordEncoder`, `AuthenticationManager` beans, access rules
- `repository/UserRepository.java` — `findByEmail`
- `dto/RegisterRequest.java`, `dto/LoginRequest.java`, `dto/AuthResponse.java`
- `exception/DuplicateEmailException.java`
- `exception/GlobalExceptionHandler.java` — `@ControllerAdvice`
- `service/AuthService.java` — register/login logic
- `controller/AuthController.java` — `POST /api/auth/register`, `POST /api/auth/login`

**Backend — modified files:**
- `application.yml` — add `jwt.secret`/`jwt.expiration-ms` reading from env vars
- `.env`, `.env.example` (repo root) — add `JWT_SECRET`, `JWT_EXPIRATION_MS`

**Frontend — new files:**
- `src/contexts/AuthContext.jsx`
- `src/pages/Login.jsx`, `src/pages/Register.jsx`
- `src/components/ProtectedRoute.jsx`, `src/components/AdminRoute.jsx`

**Frontend — modified files:**
- `src/services/api.js` — export a way for `AuthContext` to attach a 401 response interceptor
- `src/App.jsx` — wrap in `AuthProvider`, add `/login`, `/register` routes

---

## Sub-Task Breakdown (with classification)

### Backend

**B1. `UserRepository` — SIMPLE**
Single-method `JpaRepository<User, Long>` extension: `Optional<User> findByEmail(String email)`.

**B2. DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse` — SIMPLE**
Records/Lombok classes with Bean Validation annotations:
- `RegisterRequest`: `name` (`@NotBlank`), `email` (`@NotBlank @Email`), `password` (`@NotBlank @Size(min=8)`), `phone` (optional, `@Pattern` 10-digit if present).
- `LoginRequest`: `email` (`@NotBlank @Email`), `password` (`@NotBlank`).
- `AuthResponse`: `token`, `userId`, `name`, `role` — no validation, plain output DTO.

**B3. `CustomUserDetailsService` — COMPLEX (user-confirmed)**
Implements `UserDetailsService.loadUserByUsername(String email)`: looks up via `UserRepository.findByEmail`, throws `UsernameNotFoundException` if absent, wraps the `User` in a Spring Security `UserDetails` (username=email, password=hash, authority=`ROLE_<role>`).

**B4. `exception/DuplicateEmailException` + `GlobalExceptionHandler` — COMPLEX (user-confirmed)**
Custom unchecked `DuplicateEmailException`. `@ControllerAdvice` handling:
- `MethodArgumentNotValidException` → 400 with field errors
- `DuplicateEmailException` → 409
- `BadCredentialsException` → 401
- Response shape: `{timestamp, status, error, message, path}` (+ `fieldErrors[]` for validation failures)

**B5. `JwtUtil` — COMPLEX**
- `generateToken(UserDetails)`: HMAC-SHA256 signing key from `jwt.secret`, claims `userId`, `role`, 24h expiry (`jwt.expiration-ms`).
- `validateToken(String token)`: signature + expiry check, returns boolean (catches `JwtException` rather than propagating).
- `extractUsername(String token)`, `extractClaim(...)`.
Risk: wrong claim types, clock skew, swallowing exceptions incorrectly, secret key length (HS256 needs ≥256-bit key — must validate `JWT_SECRET` length at startup or fail fast).

**B6. `JwtAuthenticationFilter` — COMPLEX**
`OncePerRequestFilter`: reads `Authorization: Bearer <token>` header, skips silently if absent/malformed (lets the filter chain's access rules 401/redirect naturally rather than the filter throwing), validates token via `JwtUtil`, loads `UserDetails` via `CustomUserDetailsService`, sets `SecurityContextHolder`. Risk: must not authenticate on invalid/expired tokens; must not break public endpoints when no header is present; must not leak stack traces.

**B7. `SecurityConfig` — COMPLEX**
`SecurityFilterChain` bean: CSRF disabled, `SessionCreationPolicy.STATELESS`, authorize rules matching the three tiers exactly (order matters — most specific patterns before general ones), registers `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`. Also declares `PasswordEncoder` (BCrypt strength 10) and `AuthenticationManager` beans. Risk: highest blast radius in this task — a misordered or misspelled matcher silently opens/locks an endpoint tier.

**B8. `AuthService` — COMPLEX**
- `register(RegisterRequest)`: check `UserRepository.findByEmail` for duplicate → throw `DuplicateEmailException`; hash password via `PasswordEncoder`; save `User` with `role=USER`.
- `login(LoginRequest)`: authenticate via `AuthenticationManager` (throws `BadCredentialsException` on failure); on success, load the `User`, generate token via `JwtUtil`, return `AuthResponse`.
Risk: core business + security logic; must not leak whether an email exists on login failure vs. register conflict.

**B9. `AuthController` — SIMPLE**
Thin wrapper: `POST /api/auth/register` → `AuthService.register`, 201 + success message; `POST /api/auth/login` → `AuthService.login`, 200 + `AuthResponse`. `@Valid @RequestBody` on both.

### Frontend

**F1. `AuthContext.jsx` — COMPLEX**
State: `{user, token, isAuthenticated, isLoading}`. On mount: read token from `localStorage`, set axios default header, optimistically treat as authenticated (no `/api/users/me` exists yet — that's Task 8 — so `checkAuth` just validates presence/shape of the stored token, not a server round-trip). `login(email, password)`, `register(userData)`, `logout()`. Registers a response interceptor on the shared `api` instance that calls `logout()` on any 401. Risk: interceptor must be added once (not on every render) and ejected on unmount; token persistence across refresh; must not desync `isAuthenticated` from actual header state.

**F2. `Login.jsx` — SIMPLE**
Form (email, password), client-side required/email-format checks, calls `AuthContext.login`, shows error message, redirects to `/` on success.

**F3. `Register.jsx` — SIMPLE**
Form (name, email, phone, password, confirmPassword), client-side checks (email regex, password match, min length, phone format), calls `AuthContext.register` (i.e. `POST /api/auth/register`), redirects to `/login` on success.

**F4. `ProtectedRoute.jsx` / `AdminRoute.jsx` — COMPLEX (user-confirmed)**
Wrapper components reading `isAuthenticated` (and `user.role === 'ADMIN'` for the admin variant) from `AuthContext`, redirecting to `/login` (or `/`) when the check fails; render `children`/`<Outlet/>` otherwise.

**F5. `App.jsx` wiring — SIMPLE**
Wrap the router in `<AuthProvider>`, add `/login` and `/register` routes. No `ProtectedRoute` usages yet in this task (nothing to protect until Task 5+ pages exist) — just make the guard components available for later tasks to import.

---

## Final Classification (user-confirmed)

**SIMPLE — batch together, sanity-check only (compiles, no obvious errors):**
- B1 `UserRepository`
- B2 DTOs (`RegisterRequest`, `LoginRequest`, `AuthResponse`)
- B9 `AuthController`
- F2 `Login.jsx`
- F3 `Register.jsx`
- F5 `App.jsx` wiring

**COMPLEX — full plan → implement → test → review, one at a time:**
- B3 `CustomUserDetailsService`
- B4 `DuplicateEmailException` + `GlobalExceptionHandler`
- B5 `JwtUtil`
- B6 `JwtAuthenticationFilter`
- B7 `SecurityConfig`
- B8 `AuthService`
- F1 `AuthContext.jsx`
- F4 `ProtectedRoute.jsx` / `AdminRoute.jsx`

Execution order for the COMPLEX set follows the dependency chain: B3 → B5 → B6 → B7 → B8, then F1 → F4 (frontend `AuthContext`/route guards don't depend on backend completion to build, but are easiest to verify end-to-end once the backend endpoints are live).

A single combined review + full test run happens once at the end of all 14 sub-tasks, not per sub-task.
