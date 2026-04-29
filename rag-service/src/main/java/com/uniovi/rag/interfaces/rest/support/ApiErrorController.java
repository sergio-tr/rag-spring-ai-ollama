package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.interfaces.rest.support.dto.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ensures API clients do not receive Spring Boot's HTML whitelabel /error payloads.
 *
 * <p>This controller intentionally returns JSON for all errors. The branch goal requires JSON for
 * all `/api/**` error responses; returning JSON universally avoids accidental HTML leakage.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
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

        // Keep detail minimal to avoid leaking internals; path is useful for debugging API clients.
        String detail = path != null ? "path=" + path : null;

        return ResponseEntity
                .status(httpStatus)
                .body(ApiResponse.fail(httpStatus.name(), message, detail));
    }
}

