package com.uniovi.rag.application.service.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalizes conversation runtimeOverride into a manual-differences-only diff against a base effective config.
 */
public final class RuntimeOverrideNormalizer {

    private RuntimeOverrideNormalizer() {}

    public record NormalizedOverride(Map<String, Object> runtimeOverride, List<String> manualOverrideKeys) {}

    public static NormalizedOverride normalize(
            Map<String, Object> candidateOverride,
            Map<String, Object> baseEffectiveConfig) {
        if (candidateOverride == null || candidateOverride.isEmpty()) {
            return new NormalizedOverride(Map.of(), List.of());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : candidateOverride.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isBlank()) {
                continue;
            }
            Object candidateVal = e.getValue();
            Object baseVal = baseEffectiveConfig != null ? baseEffectiveConfig.get(k) : null;
            if (valuesEqual(candidateVal, baseVal)) {
                continue;
            }
            out.put(k, candidateVal);
        }
        List<String> keys = new ArrayList<>(out.keySet());
        keys.sort(Comparator.naturalOrder());
        return new NormalizedOverride(Map.copyOf(out), List.copyOf(keys));
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number an && b instanceof Number bn) {
            return Double.compare(an.doubleValue(), bn.doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }
}

