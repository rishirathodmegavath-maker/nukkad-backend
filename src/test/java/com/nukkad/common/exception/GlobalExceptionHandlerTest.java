package com.nukkad.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Regression guard: before these two handlers existed, a wrong-HTTP-method or unmapped-path
 *  request fell through to the generic 500 handler instead of the correct 405/404 — see
 *  GlobalExceptionHandler for the full rationale. */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongHttpMethodReturns405NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/investors");
        var ex = new HttpRequestMethodNotSupportedException("POST");

        var response = handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unmappedPathReturns404NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/does-not-exist");
        var ex = new NoHandlerFoundException("GET", "/api/does-not-exist", null);

        var response = handler.handleNoHandlerFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().errorCode()).isEqualTo("NOT_FOUND");
    }
}
