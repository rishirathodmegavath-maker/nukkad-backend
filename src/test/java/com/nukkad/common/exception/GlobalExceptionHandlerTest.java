package com.nukkad.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Regression guard: before these handlers existed, a wrong-HTTP-method, unmapped-path,
 *  wrong-Content-Type, malformed-multipart, DB-constraint, or lock-contention failure all fell
 *  through to the generic 500 handler instead of the correct 4xx/409 — see GlobalExceptionHandler
 *  for the full rationale on each. */
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

    @Test
    void wrongContentTypeOnAMultipartEndpointReturns415NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/users/me/avatar");
        var ex = new HttpMediaTypeNotSupportedException("Unsupported content type");

        var response = handler.handleUnsupportedMediaType(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().errorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void missingMultipartPartReturns400NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/users/me/avatar");
        var ex = new MissingServletRequestPartException("file");

        var response = handler.handleMissingPart(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void nonMultipartBodyOnAMultipartEndpointReturns400NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/users/me/avatar");
        var ex = new MultipartException("Current request is not a multipart request");

        var response = handler.handleMultipart(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void dbConstraintViolationReturns400NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/reports");
        var ex = new DataIntegrityViolationException("Data too long for column 'category'");

        var response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    /** Only exists to give the validation exception a real method parameter to point at. */
    @SuppressWarnings("unused")
    private void sampleEndpoint(Object body) {
    }

    @Test
    void aListItemValidationErrorNamesTheFieldWithoutTheStrayBrackets() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/startups");
        var binding = new org.springframework.validation.BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new org.springframework.validation.FieldError(
                "request", "needs[]", "each item must be 100 characters or fewer"));
        var param = new org.springframework.core.MethodParameter(
                getClass().getDeclaredMethod("sampleEndpoint", Object.class), 0);
        var ex = new org.springframework.web.bind.MethodArgumentNotValidException(param, binding);

        var response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().message()).isEqualTo("needs: each item must be 100 characters or fewer");
    }

    @Test
    void aValueThatIsTooLongForItsColumnSaysSoInsteadOfTalkingAboutConflicts() {
        when(request.getRequestURI()).thenReturn("/api/startups");
        // What MySQL raises when a startup "need" tag is longer than its VARCHAR(100) column.
        var ex = new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLException("Data truncation: Data too long for column 'need' at row 1"));

        var response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).contains("too long").doesNotContain("conflicts");
    }

    @Test
    void otherConstraintFailuresKeepTheConflictWording() {
        when(request.getRequestURI()).thenReturn("/api/chapters");
        var ex = new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLException("Duplicate entry 'Bengaluru' for key 'uq_chapters_name'"));

        var response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("conflicts with existing data");
    }

    @Test
    void lockContentionReturnsARetryable409NotAGeneric500() {
        when(request.getRequestURI()).thenReturn("/api/wallet/withdrawals");
        var ex = new CannotAcquireLockException("Deadlock found when trying to get lock");

        var response = handler.handleLockContention(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorCode()).isEqualTo("CONFLICT_RETRY");
    }
}
