package com.uniovi.rag.infrastructure.llm;

import java.util.ArrayList;
import java.util.List;

/** Normalizes CSV / list property bindings for LLM model names (preserves {@code :} and {@code /}). */
public final class LlmModelListNormalizer {

    private LlmModelListNormalizer() {}

    public static List<String> fromPropertyValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        }
        return List.copyOf(out);
    }
}
