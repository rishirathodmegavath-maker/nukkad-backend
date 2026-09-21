package com.nukkad.common.exception;

import com.nukkad.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxUploadSize;

    // Without this, an oversized upload on any endpoint (avatar, cover, feed attachment, resource
    // file...) fell through to the generic 500 handler below — a completely predictable client
    // mistake surfacing as "An unexpected error occurred" instead of a clear, actionable message.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse.Error> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("File is too large. Maximum allowed size is " + maxUploadSize + ".",
                        "FILE_TOO_LARGE", request.getRequestURI()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse.Error> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.Error.of(ex.getMessage(), ex.getErrorCode(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse.Error> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                // A constraint on each item of a list is reported against "needs[]"; the brackets mean nothing to a reader.
                .map(fe -> fe.getField().replace("[]", "") + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.Error.of(message.isBlank() ? "Validation failed" : message, "VALIDATION_ERROR", request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse.Error> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.Error.of("Invalid email or password", "UNAUTHORIZED", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse.Error> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.Error.of("You do not have permission to perform this action", "FORBIDDEN", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse.Error> handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("Malformed or invalid request body", "BAD_REQUEST", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse.Error> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("Invalid value for parameter '" + ex.getName() + "'", "BAD_REQUEST", request.getRequestURI()));
    }

    // Wrong/missing Content-Type on a multipart upload endpoint (e.g. a JSON body posted to
    // /api/users/me/avatar) previously fell through to the generic 500 handler below.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse.Error> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.Error.of("Unsupported content type. Expected " + ex.getSupportedMediaTypes(),
                        "UNSUPPORTED_MEDIA_TYPE", request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse.Error> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("Missing required part '" + ex.getRequestPartName() + "'", "BAD_REQUEST", request.getRequestURI()));
    }

    // Catches the broader multipart-parsing failures (e.g. a non-multipart body posted to a
    // multipart endpoint) that MaxUploadSizeExceededException's own, more specific handler above
    // doesn't cover.
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse.Error> handleMultipart(MultipartException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("Request must be a valid multipart/form-data upload", "BAD_REQUEST", request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse.Error> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("Missing required parameter '" + ex.getParameterName() + "'", "BAD_REQUEST", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse.Error> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of(ex.getMessage(), "BAD_REQUEST", request.getRequestURI()));
    }

    // Without these two, a request to a real path with the wrong HTTP method (e.g. POST on a
    // GET-only endpoint) or to a path with no mapping at all fell through to the generic 500
    // handler below — a routine client/API-consumer mistake surfacing as "An unexpected error
    // occurred" instead of the correct, standard 405/404.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse.Error> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.Error.of("HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                        "METHOD_NOT_ALLOWED", request.getRequestURI()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse.Error> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.Error.of("No such endpoint", "NOT_FOUND", request.getRequestURI()));
    }

    // A value that passes DTO-level @Valid but violates a DB-level constraint (oversized text
    // column, a foreign key pointing at a row that doesn't exist, a unique-index clash) previously
    // surfaced as an opaque 500 instead of a client-correctable 400.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse.Error> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        String message = isValueTooLong(ex)
                ? "One of the values you entered is too long. Please shorten it and try again."
                : "The request could not be processed because it conflicts with existing data";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of(message, "BAD_REQUEST", request.getRequestURI()));
    }

    /** MySQL's "Data truncation: Data too long for column 'x'": a value longer than the column it is stored in.
     *  The user can fix that, so they get told what to do rather than a vague "conflicts with existing data". */
    private static boolean isValueTooLong(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        return detail != null && detail.contains("Data too long for column");
    }

    // Two concurrent writes to the same row (e.g. two near-simultaneous withdrawal requests on one
    // wallet) can make MySQL pick one transaction as a deadlock victim. That's an expected,
    // retryable outcome of the row lock working as intended, not a server fault — surface it as a
    // clean, retryable 409 instead of an opaque 500.
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse.Error> handleLockContention(CannotAcquireLockException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.Error.of("This request conflicted with another update in progress. Please try again.",
                        "CONFLICT_RETRY", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse.Error> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.Error.of("An unexpected error occurred", "INTERNAL_ERROR", request.getRequestURI()));
    }
}
