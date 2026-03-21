package com.uniovi.rag.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.api.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bloquea las rutas {@code /api/**} hasta que el aprovisionamiento de modelos Ollama haya terminado
 * (o se haya omitido en tests). Evita 404/errores genéricos durante la descarga.
 */
@Component
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
            case PENDING, PULLING -> "Los modelos de Ollama se están descargando; reintenta en unos segundos.";
            case FAILED -> provisioningService.getLastError() != null
                    ? provisioningService.getLastError()
                    : "Ollama no pudo aprovisionar los modelos; revisa logs.";
            default -> "";
        };
        ApiResponse<Void> body = ApiResponse.fail(
                "OLLAMA_PROVISIONING",
                "Servicio de modelos Ollama no listo.",
                detail);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
