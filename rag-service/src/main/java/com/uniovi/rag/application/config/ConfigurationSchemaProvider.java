package com.uniovi.rag.application.config;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static JSON-schema-style description for user/project RAG configuration keys (GET config/schema).
 */
@Component
public class ConfigurationSchemaProvider {

    private static final String TYPE_BOOLEAN = "boolean";

    public Map<String, Object> buildSchema() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put(
                "fields",
                List.of(
                        textField("llmSystemPrompt", 50_000, true),
                        field("topK", "integer", 1, 100, true),
                        field("similarityThreshold", "number", 0.0, 1.0, true),
                        field("materializationStrategy", "string", null, null, true),
                        field("llmModel", "string", null, null, true),
                        field("llmTemperature", "number", 0.0, 2.0, true),
                        field("embeddingModel", "string", null, null, false),
                        field("embeddingEncodingFormat", "string", null, null, true),
                        field("embeddingDimensions", "integer", 1, 8192, true),
                        field("embeddingTimeoutSeconds", "integer", 1, 600, true),
                        field("embeddingBatchSize", "integer", 1, 512, true),
                        field("embeddingMaxInputChars", "integer", 64, 32768, true),
                        field("embeddingNormalize", "boolean", null, null, true),
                        field("embeddingTruncate", "string", null, null, true),
                        field("expansionEnabled", TYPE_BOOLEAN, null, null, true),
                        field("nerEnabled", TYPE_BOOLEAN, null, null, true),
                        field("toolsEnabled", TYPE_BOOLEAN, null, null, true),
                        field("metadataEnabled", TYPE_BOOLEAN, null, null, true)));
        return root;
    }

    private static Map<String, Object> field(
            String key, String type, Object min, Object max, boolean userEditable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("type", type);
        if (min != null) {
            m.put("min", min);
        }
        if (max != null) {
            m.put("max", max);
        }
        m.put("userEditable", userEditable);
        return m;
    }

    private static Map<String, Object> textField(String key, int maxLength, boolean userEditable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("type", "text");
        m.put("max", maxLength);
        m.put("userEditable", userEditable);
        return m;
    }
}
