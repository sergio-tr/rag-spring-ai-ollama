package com.uniovi.rag.infrastructure.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagApiPathProperties;
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
    private final RagApiPathProperties apiPathProperties;

    public OllamaProvisioningGateFilter(
            OllamaModelProvisioningService provisioningService,
            ObjectMapper objectMapper,
            RagApiPathProperties apiPathProperties) {
        this.provisioningService = provisioningService;
        this.objectMapper = objectMapper;
        this.apiPathProperties = apiPathProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api")) {
            return true;
        }
        // Auth must remain available even when Ollama models are still downloading.
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        // Allow admin endpoints to recover Ollama (trigger pulls, inspect health, etc.).
        if (path.startsWith("/api/admin/")) {
            return true;
        }
        // Legacy tooling endpoint to pull models must remain reachable too.
        if (path.contains("/ollama/")) {
            return true;
        }
        // Allow browser CORS preflight to reach the CORS/Security layers.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Only gate endpoints that actually require Ollama. Projects/config/auth/etc should work even if Ollama is down.
        if (!requiresOllama(path, request.getMethod())) {
            return true;
        }

        return provisioningService.isReadyForApiTraffic();
    }

    private boolean requiresOllama(String path, String method) {
        String legacy = apiPathProperties.getLegacyBasePath();
        String product = apiPathProperties.getProductBasePath();

        // Legacy query/evaluation call the LLM.
        if (path.startsWith(legacy + "/query") || path.startsWith(legacy + "/evaluate")) {
            return true;
        }

        // Chat message execution / retry hits the LLM. Reading messages is fine.
        if (path.startsWith(product + "/conversations/") && path.contains("/messages")) {
            return !"GET".equalsIgnoreCase(method);
        }

        // Lab evaluations and benchmarks can call LLM / embeddings.
        if (path.startsWith(product + "/lab/evaluations") || path.startsWith(product + "/lab/benchmarks")) {
            return true;
        }

        // Knowledge ingestion/rebuild can require embeddings.
        if (path.startsWith(product + "/projects/") && path.contains("/knowledge/")) {
            return true;
        }

        // Document uploads trigger ingestion/embeddings (async), so fail fast if Ollama isn't ready.
        if (path.startsWith(product + "/projects/") && path.contains("/documents")) {
            return "POST".equalsIgnoreCase(method);
        }

        // Trace replay/comparison can call the LLM.
        if (path.startsWith(product + "/runtime-traces/") && (path.contains("/replay") || path.contains("replay-comparison"))) {
            return true;
        }

        return false;
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
