package com.uniovi.rag.domain.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Per-task LLM override stored under {@code taskLlmOverrides.<taskId>} in configuration values. */
public record TaskLlmOverride(
        Boolean enabled,
        Boolean inheritModel,
        String model,
        Boolean inheritParameters,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> stop,
        Long seed,
        Double presencePenalty,
        Double frequencyPenalty,
        String responseFormat,
        Boolean think,
        Integer timeoutSeconds) {

    public TaskLlmOverride {
        stop = stop == null ? List.of() : List.copyOf(stop);
    }

    public static TaskLlmOverride fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> stop =
                readStringList(firstPresent(raw, "stopSequences", "stop"));
        return new TaskLlmOverride(
                readBool(raw.get("enabled")),
                readBool(raw.get("inheritModel")),
                readString(firstPresent(raw, "model", "modelId")),
                readBool(raw.get("inheritParameters")),
                readDouble(raw.get("temperature")),
                readDouble(firstPresent(raw, "topP", "top_p")),
                readInt(firstPresent(raw, "maxTokens", "max_tokens")),
                stop,
                readLong(raw.get("seed")),
                readDouble(firstPresent(raw, "presencePenalty", "presence_penalty")),
                readDouble(firstPresent(raw, "frequencyPenalty", "frequency_penalty")),
                readResponseFormat(raw),
                readBool(raw.get("think")),
                readInt(firstPresent(raw, "timeoutSeconds", "timeoutMs")));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (enabled != null) m.put("enabled", enabled);
        if (inheritModel != null) m.put("inheritModel", inheritModel);
        if (model != null && !model.isBlank()) m.put("model", model);
        if (inheritParameters != null) m.put("inheritParameters", inheritParameters);
        if (temperature != null) m.put("temperature", temperature);
        if (topP != null) m.put("topP", topP);
        if (maxTokens != null) m.put("maxTokens", maxTokens);
        if (!stop.isEmpty()) m.put("stopSequences", stop);
        if (seed != null) m.put("seed", seed);
        if (presencePenalty != null) m.put("presencePenalty", presencePenalty);
        if (frequencyPenalty != null) m.put("frequencyPenalty", frequencyPenalty);
        if (responseFormat != null && !responseFormat.isBlank()) m.put("responseFormat", responseFormat.trim());
        if (think != null) m.put("think", think);
        if (timeoutSeconds != null) m.put("timeoutSeconds", timeoutSeconds);
        return Map.copyOf(m);
    }

    public boolean isActive() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    public boolean effectiveInheritModel(TaskLlmTask task) {
        if (inheritModel != null) {
            return inheritModel;
        }
        boolean hasModel = model != null && !model.isBlank();
        if (hasModel) {
            return false;
        }
        return task.inheritsMainModelByDefault();
    }

    public boolean effectiveInheritParameters() {
        if (inheritParameters != null) {
            return inheritParameters;
        }
        return !hasParameterOverrides();
    }

    public TaskLlmGenerationParameters parameterOverlay() {
        return new TaskLlmGenerationParameters(
                temperature,
                topP,
                seed,
                maxTokens,
                presencePenalty,
                frequencyPenalty,
                responseFormat,
                stop,
                think,
                timeoutSeconds);
    }

    public boolean hasParameterOverrides() {
        return temperature != null
                || topP != null
                || maxTokens != null
                || seed != null
                || presencePenalty != null
                || frequencyPenalty != null
                || (responseFormat != null && !responseFormat.isBlank())
                || (stop != null && !stop.isEmpty())
                || think != null
                || timeoutSeconds != null;
    }

    public boolean hasActiveFields() {
        return (model != null && !model.isBlank())
                || inheritModel != null
                || inheritParameters != null
                || hasParameterOverrides()
                || Boolean.FALSE.equals(enabled);
    }

    private static Object firstPresent(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                return raw.get(key);
            }
        }
        return null;
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

    private static String readResponseFormat(Map<String, Object> raw) {
        Object value = firstPresent(raw, "responseFormat", "response_format");
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        if (value instanceof Map<?, ?> map && map.get("type") instanceof String type && !type.isBlank()) {
            return type.trim();
        }
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
