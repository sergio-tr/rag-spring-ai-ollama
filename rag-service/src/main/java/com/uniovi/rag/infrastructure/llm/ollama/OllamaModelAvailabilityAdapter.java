package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Uses {@link OllamaApiClient} when present; when absent (e.g. {@code test} profile without HTTP client), treats models
 * as available so unit tests can exercise orchestration without Ollama.
 */
@Component
public class OllamaModelAvailabilityAdapter implements OllamaModelAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelAvailabilityAdapter.class);
    private static final long CACHE_TTL_MS = 30_000L;

    private final ObjectProvider<OllamaApiClient> apiClientProvider;
    private final AtomicReference<CachedModelNames> cachedNames = new AtomicReference<>(CachedModelNames.empty());

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
        return matches(installedModelNames(client), modelName.trim());
    }

    @SuppressWarnings("java:S2142") // Best-effort cache refresh on servlet threads: do not leave interrupt set.
    private Set<String> installedModelNames(OllamaApiClient client) {
        CachedModelNames snapshot = cachedNames.get();
        if (snapshot.isFresh(CACHE_TTL_MS)) {
            return snapshot.names();
        }
        try {
            Set<String> names = client.listModelNames();
            CachedModelNames fresh = CachedModelNames.of(names);
            cachedNames.set(fresh);
            return fresh.names();
        } catch (IOException e) {
            log.warn("Ollama model list failed; using cached names when available: {}", e.getMessage());
            return snapshot.names();
        } catch (InterruptedException e) {
            log.warn("Ollama model list interrupted; using cached names when available");
            return snapshot.names();
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

    private record CachedModelNames(long loadedAtEpochMs, Set<String> names) {

        static CachedModelNames empty() {
            return new CachedModelNames(0L, Set.of());
        }

        static CachedModelNames of(Set<String> names) {
            return new CachedModelNames(System.currentTimeMillis(), Set.copyOf(names));
        }

        boolean isFresh(long ttlMs) {
            return !names.isEmpty() && System.currentTimeMillis() - loadedAtEpochMs < ttlMs;
        }
    }
}
