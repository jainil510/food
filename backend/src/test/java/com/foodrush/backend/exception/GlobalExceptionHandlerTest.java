package com.foodrush.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn("/api/auth/register");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "Email must be a valid email address"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/api/auth/register");
        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().get(0).field()).isEqualTo("email");
        assertThat(response.getBody().fieldErrors().get(0).message()).isEqualTo("Email must be a valid email address");
    }

    @Test
    void handleDuplicateEmail_returns409WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/auth/register");
        DuplicateEmailException ex = new DuplicateEmailException("Email already registered");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateEmail(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Email already registered");
    }

    @Test
    void handleBadCredentials_returns401WithGenericMessage_regardlessOfUnderlyingCause() {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        BadCredentialsException ex = new BadCredentialsException("User not found: nobody@foodrush.com");

        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Invalid email or password")
                .doesNotContain("nobody@foodrush.com");
    }

    @Test
    void handleRestaurantNotFound_returns404WithMessage() {
        when(request.getRequestURI()).thenReturn("/api/restaurants/99");
        RestaurantNotFoundException ex = new RestaurantNotFoundException("Restaurant not found: 99");

        ResponseEntity<ErrorResponse> response = handler.handleRestaurantNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Restaurant not found: 99");
    }

    private MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String arg) {
    }
}
