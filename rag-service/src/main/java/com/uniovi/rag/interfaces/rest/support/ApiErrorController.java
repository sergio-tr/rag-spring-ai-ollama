package com.uniovi.rag.interfaces.rest.support;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ensures API clients do not receive Spring Boot's HTML whitelabel /error payloads.
 *
 * <p>This controller intentionally returns JSON for all errors. The branch goal requires JSON for
 * all `/api/**` error responses; returning JSON universally avoids accidental HTML leakage.
 *
 * <p><strong>Error dispatch and HTTP methods:</strong> When the container forwards a failed request to
 * {@code /error}, the {@linkplain HttpServletRequest#getMethod() original method} is preserved (Servlet
 * spec / Spring Boot behaviour). A failed {@code POST} must still hit this mapping as {@code POST}.
 * Restricting to “safe” methods only would break JSON error bodies for non-GET API calls.
 *
 * <p><strong>Security:</strong> This handler is side-effect free: it reads only {@code ERROR_*}
 * request attributes and builds a response; it does not mutate server state, execute privileged logic,
 * or consume the request body. CSRF protections on state-changing routes remain on the application
 * endpoints that perform mutations; {@code /error} does not substitute for those operations.
 */
@RestController
@SuppressWarnings("java:S3752") // Accept-all methods required by Servlet error dispatch; mapping is read-only JSON (see class Javadoc).
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiErrorResponse> error(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusObj instanceof Integer ? (Integer) statusObj : 500;
        HttpStatus httpStatus = HttpStatus.resolve(status) != null ? HttpStatus.valueOf(status) : HttpStatus.INTERNAL_SERVER_ERROR;

        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String message = switch (httpStatus) {
            case NOT_FOUND -> "Not found";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case BAD_REQUEST -> "Bad request";
            default -> "Request failed";
        };

        String code = switch (httpStatus) {
            case NOT_FOUND -> "NOT_FOUND";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case BAD_REQUEST -> "BAD_REQUEST";
            default -> "INTERNAL_ERROR";
        };

        return ResponseEntity
                .status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        httpStatus.value(),
                        code,
                        message,
                        path != null ? path : request.getRequestURI(),
                        request.getHeader("X-Request-Id"),
                        null,
                        null));
    }
}

