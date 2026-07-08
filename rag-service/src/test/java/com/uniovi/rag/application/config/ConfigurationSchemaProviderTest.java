package com.uniovi.rag.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationSchemaProviderTest {

    private final ConfigurationSchemaProvider provider = new ConfigurationSchemaProvider();

    @Test
    void buildSchema_exposesCoreEditableFields() {
        Map<String, Object> schema = provider.buildSchema();

        assertThat(schema.get("version")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
        assertThat(fields).isNotEmpty();

        Map<String, Object> topK =
                fields.stream().filter(f -> "topK".equals(f.get("key"))).findFirst().orElseThrow();
        assertThat(topK.get("type")).isEqualTo("integer");
        assertThat(topK.get("min")).isEqualTo(1);
        assertThat(topK.get("max")).isEqualTo(100);

        Map<String, Object> systemPrompt =
                fields.stream().filter(f -> "llmSystemPrompt".equals(f.get("key"))).findFirst().orElseThrow();
        assertThat(systemPrompt.get("type")).isEqualTo("text");
        assertThat(systemPrompt.get("max")).isEqualTo(50_000);
    }
}
