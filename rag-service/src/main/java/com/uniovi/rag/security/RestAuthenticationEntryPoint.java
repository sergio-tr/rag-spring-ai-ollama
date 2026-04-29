package com.uniovi.rag.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Ensures protected API endpoints return JSON (not HTML) on 401.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final byte[] BODY = "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized\"}}"
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(BODY);
    }
}

