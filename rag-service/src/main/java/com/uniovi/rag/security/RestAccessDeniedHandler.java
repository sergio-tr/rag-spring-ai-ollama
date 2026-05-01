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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Ensures protected API endpoints return JSON (not HTML) on 403.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        Instant.now(),
                        HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN",
                        "Forbidden",
                        request.getRequestURI(),
                        request.getHeader("X-Request-Id"),
                        null));
    }
}

