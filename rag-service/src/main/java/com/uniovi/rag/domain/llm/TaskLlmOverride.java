package com.uniovi.rag.domain.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Per-task LLM override stored under {@code taskLlmOverrides.<taskId>} in configuration values. */
public record TaskLlmOverride(
        Boolean enabled,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> stop,
        Long seed) {

    public TaskLlmOverride {
        stop = stop == null ? List.of() : List.copyOf(stop);
    }

    public static TaskLlmOverride fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return new TaskLlmOverride(
                readBool(raw.get("enabled")),
                readString(raw.get("model")),
                readDouble(raw.get("temperature")),
                readDouble(raw.get("topP")),
                readInt(raw.get("maxTokens")),
                readStringList(raw.get("stop")),
                readLong(raw.get("seed")));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (enabled != null) m.put("enabled", enabled);
        if (model != null && !model.isBlank()) m.put("model", model);
        if (temperature != null) m.put("temperature", temperature);
        if (topP != null) m.put("topP", topP);
        if (maxTokens != null) m.put("maxTokens", maxTokens);
        if (!stop.isEmpty()) m.put("stop", stop);
        if (seed != null) m.put("seed", seed);
        return Map.copyOf(m);
    }

    public boolean isActive() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    private static Boolean readBool(Object raw) {
        return raw instanceof Boolean b ? b : null;
    }

    private static String readString(Object raw) {
        return raw instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private static Double readDouble(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        return null;
    }

    private static Integer readInt(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        return null;
    }

    private static Long readLong(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
