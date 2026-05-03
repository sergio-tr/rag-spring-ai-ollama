package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.interfaces.rest.auth.AuthTokenException;
import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.EmailNotVerifiedException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.FeatureDisabledException;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import com.uniovi.rag.interfaces.rest.support.dto.ApiValidationError;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.HttpStatusCode;

import java.util.stream.Collectors;

/**
 * Standardizes JSON error responses for REST controllers.
 *
 * <p>Success payloads may remain endpoint-specific (e.g. auth LoginResponse), but failures must not
 * fall back to HTML whitelabel pages for API clients.
 */
@RestControllerAdvice(basePackages = "com.uniovi.rag.interfaces.rest")
public class ApiGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ApiValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiValidationError(e.getField(), safeMessage(e.getDefaultMessage(), "invalid")))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(build(
                servletRequestOrNull(request),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiValidationError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiValidationError(
                        v.getPropertyPath() != null ? v.getPropertyPath().toString() : null,
                        safeMessage(v.getMessage(), "invalid")))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(build(
                request,
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(build(
                servletRequestOrNull(request),
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Invalid JSON body",
                null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(build(
                request,
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                "Invalid request parameter",
                null));
    }

    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(build(
                servletRequestOrNull(request),
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Method not allowed",
                null));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(build(
                servletRequestOrNull(request),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported media type",
                null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus st = HttpStatus.resolve(ex.getStatusCode().value()) != null
                ? HttpStatus.valueOf(ex.getStatusCode().value())
                : HttpStatus.INTERNAL_SERVER_ERROR;
        String msg = ex.getReason() != null && !ex.getReason().isBlank() ? ex.getReason() : "Request failed";
        return ResponseEntity.status(st).body(build(request, st, st.name(), msg, null));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(build(
                request,
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                "Email already registered",
                null));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(build(
                request,
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid credentials",
                null));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailNotVerified(EmailNotVerifiedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(build(
                request,
                HttpStatus.FORBIDDEN,
                "EMAIL_NOT_VERIFIED",
                "Email verification required",
                null));
    }

    @ExceptionHandler(AuthTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthToken(AuthTokenException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(build(
                request,
                HttpStatus.BAD_REQUEST,
                safeCode(ex.getCode(), "INVALID_TOKEN"),
                safeMessage(ex.getPublicMessage(), "Invalid token"),
                null));
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleFeatureDisabled(FeatureDisabledException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(build(
                request,
                HttpStatus.NOT_FOUND,
                safeCode(ex.getCode(), "NOT_FOUND"),
                safeMessage(ex.getPublicMessage(), "Not found"),
                null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(build(
                request,
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                safeMessage(ex.getMessage(), "Not found"),
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(build(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                null));
    }

    private static ApiErrorResponse build(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            List<ApiValidationError> validationErrors) {
        String path = request != null ? request.getRequestURI() : null;
        String requestId = request != null ? headerFirstNonBlank(request, "X-Request-Id", "x-request-id") : null;
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                safeCode(code, status.name()),
                safeMessage(message, "Request failed"),
                path,
                requestId,
                (validationErrors == null || validationErrors.isEmpty()) ? null : validationErrors);
    }

    private static HttpServletRequest servletRequestOrNull(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest();
        }
        return null;
    }

    private static String headerFirstNonBlank(HttpServletRequest request, String... names) {
        for (String name : names) {
            String v = request.getHeader(name);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String safeCode(String raw, String fallback) {
        String s = raw != null ? raw.trim() : "";
        return s.isEmpty() ? fallback : s;
    }

    private static String safeMessage(String raw, String fallback) {
        String s = raw != null ? raw.trim() : "";
        return s.isEmpty() ? fallback : s;
    }
}

