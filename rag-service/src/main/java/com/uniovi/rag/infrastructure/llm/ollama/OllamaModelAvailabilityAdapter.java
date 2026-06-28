package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Uses {@link OllamaApiClient} when present; when absent (e.g. {@code test} profile without HTTP client), treats models
 * as available so unit tests can exercise orchestration without Ollama.
 */
@Component
public class OllamaModelAvailabilityAdapter implements OllamaModelAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelAvailabilityAdapter.class);

    private final ObjectProvider<OllamaApiClient> apiClientProvider;

    public OllamaModelAvailabilityAdapter(ObjectProvider<OllamaApiClient> apiClientProvider) {
        this.apiClientProvider = apiClientProvider;
    }

    @Override
    public boolean isModelPresent(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        OllamaApiClient client = apiClientProvider.getIfAvailable();
        if (client == null) {
            return true;
        }
        try {
            Set<String> names = client.listModelNames();
            return matches(names, modelName.trim());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ollama model list failed; treating model as unavailable: {}", e.getMessage());
            return false;
        }
    }

    static boolean matches(Set<String> installedNames, String requested) {
        if (installedNames.contains(requested)) {
            return true;
        }
        String reqLower = requested.toLowerCase(Locale.ROOT);
        for (String n : installedNames) {
            if (n != null && (n.equalsIgnoreCase(requested) || reqLower.regionMatches(0, n.toLowerCase(Locale.ROOT), 0, reqLower.length()))) {
                return true;
            }
        }
        return false;
    }
}
