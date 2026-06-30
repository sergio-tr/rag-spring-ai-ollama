package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Mutable partial LLM settings used while merging configuration layers. */
final class LlmConfigurationLayer {

    LlmProvider chatProvider;
    LlmProvider embeddingProvider;
    String baseUrl;
    String chatModel;
    String embeddingModel;
    String apiKeyEnv;
    String secretName;
    Double temperature;
    Integer timeoutMs;
    String systemPrompt;
    Map<String, Object> additionalParameters = Map.of();

    static LlmConfigurationLayer empty() {
        return new LlmConfigurationLayer();
    }

    LlmConfigurationLayer mergeOver(LlmConfigurationLayer overlay) {
        if (overlay == null) {
            return this;
        }
        if (overlay.chatProvider != null) {
            this.chatProvider = overlay.chatProvider;
        }
        if (overlay.embeddingProvider != null) {
            this.embeddingProvider = overlay.embeddingProvider;
        }
        if (overlay.baseUrl != null && !overlay.baseUrl.isBlank()) {
            this.baseUrl = overlay.baseUrl.trim();
        }
        if (overlay.chatModel != null && !overlay.chatModel.isBlank()) {
            this.chatModel = overlay.chatModel.trim();
        }
        if (overlay.embeddingModel != null && !overlay.embeddingModel.isBlank()) {
            this.embeddingModel = overlay.embeddingModel.trim();
        }
        if (overlay.apiKeyEnv != null && !overlay.apiKeyEnv.isBlank()) {
            this.apiKeyEnv = overlay.apiKeyEnv.trim();
        }
        if (overlay.secretName != null && !overlay.secretName.isBlank()) {
            this.secretName = overlay.secretName.trim();
        }
        if (overlay.temperature != null) {
            this.temperature = overlay.temperature;
        }
        if (overlay.timeoutMs != null) {
            this.timeoutMs = overlay.timeoutMs;
        }
        if (overlay.systemPrompt != null) {
            this.systemPrompt = overlay.systemPrompt;
        }
        if (overlay.additionalParameters != null && !overlay.additionalParameters.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(this.additionalParameters);
            merged.putAll(overlay.additionalParameters);
            this.additionalParameters = Map.copyOf(merged);
        }
        return this;
    }

    static LlmConfigurationLayer fromMap(Map<String, Object> values, ObjectMapper objectMapper) {
        if (values == null || values.isEmpty()) {
            return empty();
        }
        JsonNode json = objectMapper.valueToTree(values);
        return fromJson(json);
    }

    static LlmConfigurationLayer fromJson(JsonNode json) {
        LlmConfigurationLayer layer = empty();
        if (json == null || json.isNull() || json.isMissingNode() || json.isEmpty()) {
            return layer;
        }
        layer.chatProvider = readProvider(json, LlmConfigurationKeys.CHAT_PROVIDER);
        layer.embeddingProvider = readProvider(json, LlmConfigurationKeys.EMBEDDING_PROVIDER);
        LlmProvider legacyProvider = readProvider(json, LlmConfigurationKeys.PROVIDER);
        if (layer.chatProvider == null) {
            layer.chatProvider = legacyProvider;
        }
        if (layer.embeddingProvider == null) {
            layer.embeddingProvider = legacyProvider;
        }
        layer.baseUrl = readText(json, LlmConfigurationKeys.BASE_URL);
        layer.chatModel = readText(json, LlmConfigurationKeys.CHAT_MODEL);
        layer.embeddingModel = readText(json, LlmConfigurationKeys.EMBEDDING_MODEL);
        layer.apiKeyEnv = readText(json, LlmConfigurationKeys.API_KEY_ENV);
        layer.secretName = readText(json, LlmConfigurationKeys.SECRET_NAME);
        layer.temperature = readDouble(json, LlmConfigurationKeys.TEMPERATURE);
        layer.timeoutMs = readInt(json, LlmConfigurationKeys.TIMEOUT_MS);
        layer.systemPrompt = readText(json, LlmConfigurationKeys.SYSTEM_PROMPT);
        layer.additionalParameters = readAdditionalParameters(json);
        return layer;
    }

    private static LlmProvider readProvider(JsonNode json, String field) {
        if (!json.hasNonNull(field) || !json.get(field).isTextual()) {
            return null;
        }
        String raw = json.get(field).asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LlmProvider.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String readText(JsonNode json, String field) {
        if (!json.hasNonNull(field) || !json.get(field).isTextual()) {
            return null;
        }
        String value = json.get(field).asText();
        return value != null && !value.isBlank() ? value : null;
    }

    private static Integer readInt(JsonNode json, String field) {
        if (!json.hasNonNull(field) || !json.get(field).isNumber()) {
            return null;
        }
        return json.get(field).asInt();
    }

    private static Double readDouble(JsonNode json, String field) {
        if (!json.hasNonNull(field) || !json.get(field).isNumber()) {
            return null;
        }
        return json.get(field).asDouble();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readAdditionalParameters(JsonNode json) {
        if (!json.hasNonNull(LlmConfigurationKeys.ADDITIONAL_PARAMETERS)
                || !json.get(LlmConfigurationKeys.ADDITIONAL_PARAMETERS).isObject()) {
            return Map.of();
        }
        JsonNode node = json.get(LlmConfigurationKeys.ADDITIONAL_PARAMETERS);
        Map<String, Object> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> out.put(entry.getKey(), objectToValue(entry.getValue())));
        return out.isEmpty() ? Map.of() : Map.copyOf(out);
    }

    private static Object objectToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.toString();
    }

    static Optional<LlmConfigurationLayer> optionalFromMap(Map<String, Object> values, ObjectMapper objectMapper) {
        LlmConfigurationLayer layer = fromMap(values, objectMapper);
        return layer.isEmpty() ? Optional.empty() : Optional.of(layer);
    }

    boolean isEmpty() {
        return chatProvider == null
                && embeddingProvider == null
                && (baseUrl == null || baseUrl.isBlank())
                && (chatModel == null || chatModel.isBlank())
                && (embeddingModel == null || embeddingModel.isBlank())
                && (apiKeyEnv == null || apiKeyEnv.isBlank())
                && (secretName == null || secretName.isBlank())
                && temperature == null
                && timeoutMs == null
                && (systemPrompt == null || systemPrompt.isBlank())
                && (additionalParameters == null || additionalParameters.isEmpty());
    }
}
