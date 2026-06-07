package com.uniovi.rag.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import java.time.Instant;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Ensures protected API endpoints return JSON (not HTML) on 401.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    public RestAuthenticationEntryPoint(Object ignored) {
        // Kept for binary compatibility with SecurityConfiguration wiring.
        // No state required: unauthorized responses are always 401.
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        int status = HttpServletResponse.SC_UNAUTHORIZED;
        String code = "UNAUTHENTICATED";
        String message = "Authentication required";

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        Instant.now(),
                        status,
                        code,
                        message,
                        request.getRequestURI(),
                        request.getHeader("X-Request-Id"),
                        null,
                        null));
    }
}

