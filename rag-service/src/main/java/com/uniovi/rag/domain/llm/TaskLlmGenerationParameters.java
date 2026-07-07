package com.uniovi.rag.domain.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Generation parameters for a task LLM role (effective or override subset). */
public record TaskLlmGenerationParameters(
        Double temperature,
        Double topP,
        Long seed,
        Integer maxTokens,
        Double presencePenalty,
        Double frequencyPenalty,
        String responseFormat,
        List<String> stopSequences,
        Boolean think,
        Integer timeoutSeconds) {

    public TaskLlmGenerationParameters {
        stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
        if (responseFormat != null && responseFormat.isBlank()) {
            responseFormat = null;
        }
    }

    public static TaskLlmGenerationParameters empty() {
        return new TaskLlmGenerationParameters(null, null, null, null, null, null, null, List.of(), null, null);
    }

    public static TaskLlmGenerationParameters fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        return new TaskLlmGenerationParameters(
                readDouble(raw.get("temperature")),
                readDouble(firstPresent(raw, "topP", "top_p")),
                readLong(raw.get("seed")),
                readInt(firstPresent(raw, "maxTokens", "max_tokens")),
                readDouble(firstPresent(raw, "presencePenalty", "presence_penalty")),
                readDouble(firstPresent(raw, "frequencyPenalty", "frequency_penalty")),
                readString(firstPresent(raw, "responseFormat", "response_format")),
                readStringList(firstPresent(raw, "stopSequences", "stop")),
                readBool(raw.get("think")),
                readInt(firstPresent(raw, "timeoutSeconds", "timeoutMs")));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (temperature != null) m.put("temperature", temperature);
        if (topP != null) m.put("topP", topP);
        if (seed != null) m.put("seed", seed);
        if (maxTokens != null) m.put("maxTokens", maxTokens);
        if (presencePenalty != null) m.put("presencePenalty", presencePenalty);
        if (frequencyPenalty != null) m.put("frequencyPenalty", frequencyPenalty);
        if (responseFormat != null && !responseFormat.isBlank()) m.put("responseFormat", responseFormat.trim());
        if (!stopSequences.isEmpty()) m.put("stopSequences", stopSequences);
        if (think != null) m.put("think", think);
        if (timeoutSeconds != null) m.put("timeoutSeconds", timeoutSeconds);
        return Map.copyOf(m);
    }

    public TaskLlmGenerationParameters mergeOverlay(TaskLlmGenerationParameters overlay) {
        if (overlay == null) {
            return this;
        }
        return new TaskLlmGenerationParameters(
                overlay.temperature != null ? overlay.temperature : temperature,
                overlay.topP != null ? overlay.topP : topP,
                overlay.seed != null ? overlay.seed : seed,
                overlay.maxTokens != null ? overlay.maxTokens : maxTokens,
                overlay.presencePenalty != null ? overlay.presencePenalty : presencePenalty,
                overlay.frequencyPenalty != null ? overlay.frequencyPenalty : frequencyPenalty,
                overlay.responseFormat != null ? overlay.responseFormat : responseFormat,
                !overlay.stopSequences.isEmpty() ? overlay.stopSequences : stopSequences,
                overlay.think != null ? overlay.think : think,
                overlay.timeoutSeconds != null ? overlay.timeoutSeconds : timeoutSeconds);
    }

    public Map<String, Object> toAdditionalParameters() {
        Map<String, Object> additional = new LinkedHashMap<>();
        if (topP != null) additional.put("topP", topP);
        if (maxTokens != null) additional.put("maxTokens", maxTokens);
        if (seed != null) additional.put("seed", seed);
        if (presencePenalty != null) additional.put("presencePenalty", presencePenalty);
        if (frequencyPenalty != null) additional.put("frequencyPenalty", frequencyPenalty);
        if (!stopSequences.isEmpty()) additional.put("stop", stopSequences);
        if (think != null) additional.put("think", think);
        if (responseFormat != null && !responseFormat.isBlank() && !"text".equalsIgnoreCase(responseFormat.trim())) {
            String fmt = responseFormat.trim();
            if ("json_object".equalsIgnoreCase(fmt)) {
                additional.put("responseFormat", Map.of("type", "json_object"));
            }
        }
        return Map.copyOf(additional);
    }

    public boolean hasAnyField() {
        return temperature != null
                || topP != null
                || seed != null
                || maxTokens != null
                || presencePenalty != null
                || frequencyPenalty != null
                || (responseFormat != null && !responseFormat.isBlank())
                || !stopSequences.isEmpty()
                || think != null
                || timeoutSeconds != null;
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
        if (raw instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        if (raw instanceof Map<?, ?> map && map.get("type") instanceof String type && !type.isBlank()) {
            return type.trim();
        }
        return null;
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
