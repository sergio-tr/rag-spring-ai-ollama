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

    public Map<String, Object> buildSchema() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put(
                "fields",
                List.of(
                        field("topK", "integer", 1, 100, true),
                        field("similarityThreshold", "number", 0.0, 1.0, true),
                        field("llmModel", "string", null, null, true),
                        field("embeddingModel", "string", null, null, false),
                        field("expansionEnabled", "boolean", null, null, true),
                        field("nerEnabled", "boolean", null, null, true),
                        field("toolsEnabled", "boolean", null, null, true),
                        field("metadataEnabled", "boolean", null, null, true)));
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
}
