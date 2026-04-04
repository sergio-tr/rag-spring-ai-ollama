package com.uniovi.rag.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.domain.config.prompt.PromptFragment;
import com.uniovi.rag.domain.config.prompt.PromptStack;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SHA-256 over canonical JSON of {@link ResolvedRuntimeConfig} for {@code resolved_config_snapshot.config_hash}.
 * Key order is fixed for stable hashes.
 */
public final class ResolvedRuntimeConfigHasher {

    private static final ObjectMapper CANONICAL_JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ResolvedRuntimeConfigHasher() {}

    public static String sha256Hex(ResolvedRuntimeConfig r) {
        try {
            byte[] utf8 = CANONICAL_JSON.writeValueAsString(toOrderedMap(r)).getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(utf8);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash ResolvedRuntimeConfig", e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toOrderedMap(ResolvedRuntimeConfig r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resolvedCoreConfig", r.resolvedCoreConfig() != null ? r.resolvedCoreConfig().toValueMap() : Map.of());
        m.put("capabilitySet", r.capabilitySet() != null ? CANONICAL_JSON.convertValue(r.capabilitySet(), Map.class) : Map.of());
        m.put(
                "compatibilityResult",
                r.compatibility() != null ? CANONICAL_JSON.convertValue(r.compatibility(), Map.class) : Map.of());
        m.put("promptStack", promptStackToMap(r.promptStack()));
        m.put(
                "provenance",
                r.provenance() != null ? CANONICAL_JSON.convertValue(r.provenance(), Map.class) : Map.of());
        m.put(
                "reindexPreview",
                r.reindexPreview() != null ? CANONICAL_JSON.convertValue(r.reindexPreview(), Map.class) : Map.of());
        m.put(
                "legacyProjection",
                r.legacyProjection() != null ? r.legacyProjection().toValueMap() : Map.of());
        return m;
    }

    private static Map<String, Object> promptStackToMap(PromptStack stack) {
        if (stack == null || stack.fragments() == null) {
            return Map.of("fragments", List.of());
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (PromptFragment f : stack.fragments()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("role", f.role() != null ? f.role().name() : "");
            row.put("sourceLabel", f.sourceLabel() != null ? f.sourceLabel() : "");
            row.put("text", f.text() != null ? f.text() : "");
            rows.add(row);
        }
        return Map.of("fragments", rows);
    }
}
