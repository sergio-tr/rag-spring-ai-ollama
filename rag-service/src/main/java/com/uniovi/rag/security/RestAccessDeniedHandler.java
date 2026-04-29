package com.uniovi.rag.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Ensures protected API endpoints return JSON (not HTML) on 403.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final byte[] BODY = "{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Forbidden\"}}"
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(BODY);
    }
}

