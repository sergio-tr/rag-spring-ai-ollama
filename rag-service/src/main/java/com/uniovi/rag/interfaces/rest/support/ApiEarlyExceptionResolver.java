package com.uniovi.rag.interfaces.rest.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Resolves framework-level exceptions that happen before handler method execution (e.g. 405/415)
 * into the canonical JSON error contract.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiEarlyExceptionResolver implements HandlerExceptionResolver {

    private static final Set<String> API_PREFIXES = Set.of("/api/");

    private final ObjectMapper objectMapper;

    public ApiEarlyExceptionResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        String path = request != null ? request.getRequestURI() : null;
        if (path == null || API_PREFIXES.stream().noneMatch(path::startsWith)) {
            return null;
        }

        if (ex instanceof HttpRequestMethodNotSupportedException) {
            write(response, new ApiErrorResponse(Instant.now(), 405, "METHOD_NOT_ALLOWED", "Method not allowed", path, requestId(request), null));
            return new ModelAndView();
        }

        if (ex instanceof HttpMediaTypeNotSupportedException) {
            write(response, new ApiErrorResponse(Instant.now(), 415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", path, requestId(request), null));
            return new ModelAndView();
        }

        return null;
    }

    private void write(HttpServletResponse response, ApiErrorResponse body) {
        if (response == null || response.isCommitted()) return;
        try {
            response.setStatus(body.status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), body);
        } catch (Exception ignored) {
            // If we cannot write a JSON body, fall back to the container defaults.
        }
    }

    private static String requestId(HttpServletRequest request) {
        if (request == null) return null;
        String v = request.getHeader("X-Request-Id");
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}

