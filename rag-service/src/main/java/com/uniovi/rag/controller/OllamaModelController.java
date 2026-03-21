package com.uniovi.rag.controller;

import com.uniovi.rag.api.OllamaConnectivityChecker;
import com.uniovi.rag.api.dto.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints para el lab: forzar comprobar conectividad con Ollama y descargar modelos (embedding + chat)
 * vía {@code POST /api/pull} en el host configurado en {@code spring.ai.ollama.base-url}.
 */
@RestController
@RequestMapping("/api/v4/ollama")
public class OllamaModelController {

    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    public OllamaModelController(OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
    }

    /**
     * Cuerpo opcional: {@code { "chatModel": "nombre:tag" }}. Si se omite, se usa solo el modelo de chat por defecto.
     */
    @PostMapping(value = "/models/ensure", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> ensureModels(@RequestBody(required = false) Map<String, String> body) {
        String chatModel = body != null ? body.get("chatModel") : null;
        ollamaConnectivityChecker.prepareForQuery(chatModel);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "ok", true,
                "reachable", ollamaConnectivityChecker.isOllamaReachable()
        )));
    }
}
