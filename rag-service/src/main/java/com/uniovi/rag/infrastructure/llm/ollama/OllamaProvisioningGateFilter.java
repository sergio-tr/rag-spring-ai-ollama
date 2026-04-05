package com.uniovi.rag.infrastructure.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.interfaces.rest.support.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Blocks {@code /api/**} routes until Ollama model provisioning has finished
 * (or was skipped in tests). Avoids generic 404/errors while models are downloading.
 * Not registered under profile {@code test} (integration tests use stubs / no real pull gate).
 */
@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OllamaProvisioningGateFilter extends OncePerRequestFilter {

    private final OllamaModelProvisioningService provisioningService;
    private final ObjectMapper objectMapper;

    public OllamaProvisioningGateFilter(
            OllamaModelProvisioningService provisioningService,
            ObjectMapper objectMapper) {
        this.provisioningService = provisioningService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api")) {
            return true;
        }
        return provisioningService.isReadyForApiTraffic();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        OllamaModelProvisioningService.State state = provisioningService.getState();
        String detail = switch (state) {
            case PENDING, PULLING -> "Ollama models are downloading; retry in a few seconds.";
            case FAILED -> provisioningService.getLastError() != null
                    ? provisioningService.getLastError()
                    : "Ollama could not provision models; check logs.";
            default -> "";
        };
        ApiResponse<Void> body = ApiResponse.fail(
                "OLLAMA_PROVISIONING",
                "Ollama model service not ready.",
                detail);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
