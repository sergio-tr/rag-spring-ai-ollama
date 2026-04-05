package com.uniovi.rag.interfaces.rest.legacy;

import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.interfaces.rest.support.dto.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lab endpoints: trigger Ollama connectivity checks and model downloads (embedding + chat)
 * via {@code POST /api/pull} on the host configured in {@code spring.ai.ollama.base-url}.
 */
@RestController
@RequestMapping("${rag.api.legacy-base-path}/ollama")
@ConditionalOnProperty(prefix = "rag.api", name = "legacy-controllers-enabled", havingValue = "true", matchIfMissing = true)
public class OllamaModelController {

    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    public OllamaModelController(OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
    }

    /**
     * Optional body: {@code { "chatModel": "name:tag" }}. If omitted, only the default chat model is used.
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
