package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.interfaces.rest.auth.AuthTokenException;
import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.EmailNotVerifiedException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.FeatureDisabledException;
import com.uniovi.rag.application.service.evaluation.RagBenchmarkHumanReasons;
import com.uniovi.rag.application.service.evaluation.config.LabRuntimeConfigReasonCodes;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetValidationException;
import com.uniovi.rag.application.service.evaluation.LabDatasetGateException;
import com.uniovi.rag.application.service.evaluation.LabJobConcurrencyException;
import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityException;
import com.uniovi.rag.application.config.PromptTemplateValidationException;
import com.uniovi.rag.application.service.chat.RuntimeConfigurationInvalidException;
import com.uniovi.rag.application.service.admin.model.AdminModelCheckException;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetValidationFailedDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetValidationReportDto;
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
import org.springframework.lang.Nullable;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import java.util.LinkedHashMap;
import java.util.Map;

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
            @Nullable HttpHeaders headers,
            @Nullable HttpStatusCode status,
            WebRequest request) {
        List<ApiValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiValidationError(e.getField(), trimOrFallback(e.getDefaultMessage(), "invalid")))
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
                        trimOrFallback(v.getMessage(), "invalid")))
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
            @Nullable HttpHeaders headers,
            @Nullable HttpStatusCode status,
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

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            @Nullable HttpHeaders headers,
            @Nullable HttpStatusCode status,
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
            @Nullable HttpHeaders headers,
            @Nullable HttpStatusCode status,
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
        String reason = ex.getReason() != null && !ex.getReason().isBlank() ? ex.getReason().trim() : null;
        if (reason != null
                && (LabCorpusReasonCodes.isReasonCode(reason) || LabRuntimeConfigReasonCodes.isConfigReasonCode(reason))) {
            String human = RagBenchmarkHumanReasons.humanize(reason);
            return ResponseEntity.status(st).body(build(request, st, reason, human, null));
        }
        String msg = reason != null ? reason : "Request failed";
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
                trimOrFallback(ex.getCode(), "INVALID_TOKEN"),
                trimOrFallback(ex.getPublicMessage(), "Invalid token"),
                null));
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleFeatureDisabled(FeatureDisabledException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(build(
                request,
                HttpStatus.NOT_FOUND,
                trimOrFallback(ex.getCode(), "NOT_FOUND"),
                trimOrFallback(ex.getPublicMessage(), "Not found"),
                null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(build(
                request,
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                trimOrFallback(ex.getMessage(), "Not found"),
                null));
    }

    @ExceptionHandler(ExperimentalDatasetValidationException.class)
    public ResponseEntity<ExperimentalDatasetValidationFailedDto> handleExperimentalDatasetInvalid(
            ExperimentalDatasetValidationException ex, HttpServletRequest request) {
        ExperimentalDatasetValidationReportDto report =
                ExperimentalDatasetValidationReportDto.from(ex.validationReport());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ExperimentalDatasetValidationFailedDto.of(report));
    }

    @ExceptionHandler(LabDatasetGateException.class)
    public ResponseEntity<ApiErrorResponse> handleLabDatasetGate(LabDatasetGateException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("validationIssues", ex.validationReport().issues().stream().map(ApiGlobalExceptionHandler::issueMap).toList());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                trimOrFallback(ex.code(), "DATASET_INVALID"),
                trimOrFallback(ex.getMessage(), "Dataset is not eligible for this Lab benchmark"),
                request != null ? request.getRequestURI() : null,
                request != null ? headerFirstNonBlank(request, "X-Request-Id", "x-request-id") : null,
                null,
                details
        ));
    }

    @ExceptionHandler(LabJobConcurrencyException.class)
    public ResponseEntity<ApiErrorResponse> handleLabJobConcurrency(LabJobConcurrencyException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex.activeJob() != null) {
            details.put("activeJob", ex.activeJob());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "LAB_JOB_ALREADY_RUNNING",
                trimOrFallback(ex.getMessage(), "A Lab job is already running"),
                request != null ? request.getRequestURI() : null,
                request != null ? headerFirstNonBlank(request, "X-Request-Id", "x-request-id") : null,
                null,
                details
        ));
    }

    @ExceptionHandler(EmbeddingIndexCompatibilityException.class)
    public ResponseEntity<ApiErrorResponse> handleEmbeddingIndexCompatibility(
            EmbeddingIndexCompatibilityException ex, HttpServletRequest request) {
        Map<String, Object> details =
                ex.details() != null && !ex.details().isEmpty() ? new LinkedHashMap<>(ex.details()) : null;
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(
                        new ApiErrorResponse(
                                Instant.now(),
                                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                trimOrFallback(ex.code(), "NO_COMPATIBLE_VECTOR_INDEX"),
                                trimOrFallback(ex.getMessage(), "Embedding index compatibility check failed"),
                                request != null ? request.getRequestURI() : null,
                                request != null ? headerFirstNonBlank(request, "X-Request-Id", "x-request-id") : null,
                                null,
                                details));
    }

    @ExceptionHandler(AdminModelCheckException.class)
    public ResponseEntity<ApiErrorResponse> handleAdminModelCheck(AdminModelCheckException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(build(
                request,
                HttpStatus.UNPROCESSABLE_ENTITY,
                trimOrFallback(ex.code(), "MODEL_INVALID"),
                trimOrFallback(ex.getMessage(), "Model check failed"),
                null));
    }

    @ExceptionHandler(PromptTemplateValidationException.class)
    public ResponseEntity<ApiErrorResponse> handlePromptTemplateInvalid(
            PromptTemplateValidationException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>(ex.toDetailsMap());
        List<ApiValidationError> validationErrors = List.of(
                new ApiValidationError(
                        ex.field() != null ? ex.field() : "promptOverrides",
                        trimOrFallback(ex.getMessage(), "Invalid prompt template")));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        new ApiErrorResponse(
                                Instant.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                PromptTemplateValidationException.ERROR_CODE,
                                trimOrFallback(ex.getMessage(), "Invalid prompt template"),
                                request != null ? request.getRequestURI() : null,
                                request != null ? headerFirstNonBlank(request, "X-Request-Id", "x-request-id") : null,
                                validationErrors,
                                details));
    }

    @ExceptionHandler(RuntimeConfigurationInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeConfigurationInvalid(
            RuntimeConfigurationInvalidException ex,
            HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(
                "issues",
                ex.issues().stream()
                        .map(i -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("code", i.code());
                            m.put("field", i.field());
                            m.put("message", i.message());
                            m.put("severity", i.severity());
                            return m;
                        })
                        .toList());
        HttpStatus status = HttpStatus.resolve(ex.httpStatus());
        if (status == null) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return ResponseEntity.status(status)
                .body(
                        new ApiErrorResponse(
                                Instant.now(),
                                status.value(),
                                trimOrFallback(ex.code(), "RUNTIME_CONFIGURATION_INVALID"),
                                trimOrFallback(ex.getMessage(), "Runtime configuration is invalid"),
                                request != null ? request.getRequestURI() : null,
                                request != null ? headerFirstNonBlank(request, "X-Request-Id", "x-request-id") : null,
                                null,
                                details));
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
                trimOrFallback(code, status.name()),
                trimOrFallback(message, "Request failed"),
                path,
                requestId,
                (validationErrors == null || validationErrors.isEmpty()) ? null : validationErrors,
                null);
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

    private static String trimOrFallback(String raw, String fallback) {
        String s = raw != null ? raw.trim() : "";
        return s.isEmpty() ? fallback : s;
    }

    private static Map<String, Object> issueMap(ValidationIssue i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", i.severity().name());
        m.put("code", i.code().name());
        m.put("sheet", i.sheet());
        m.put("rowNumber", i.rowNumber());
        m.put("column", i.column());
        m.put("message", i.message());
        return m;
    }
}

