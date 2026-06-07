package com.uniovi.rag.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelemetryRedactionTest {

    @Test
    void safeAttributes_stripsRawQuery_andEmitsQueryLength() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("query", "user secret text");
        input.put("conversationId", UUID_PLACEHOLDER);

        Map<String, String> safe = TelemetryRedaction.safeAttributes(input);

        assertThat(safe).doesNotContainKey("query");
        assertThat(safe).containsEntry("queryLength", "16");
        assertThat(safe).containsEntry("conversationId", UUID_PLACEHOLDER);
    }

    @Test
    void safeAttributes_deniesPromptContentAndSecrets() {
        Map<String, String> input = Map.of(
                "prompt", "system prompt",
                "content", "hello",
                "token", "abc",
                "presetKey", "P0");

        Map<String, String> safe = TelemetryRedaction.safeAttributes(input);

        assertThat(safe).containsEntry("presetKey", "P0");
        assertThat(safe).containsEntry("queryLength", "5");
        assertThat(safe).doesNotContainKeys("prompt", "content", "token");
    }

    @Test
    void isForbiddenKey_normalizesCaseAndSeparators() {
        assertThat(TelemetryRedaction.isForbiddenKey("Authorization")).isTrue();
        assertThat(TelemetryRedaction.isForbiddenKey("user-text")).isTrue();
        assertThat(TelemetryRedaction.isForbiddenKey("presetKey")).isFalse();
    }

    private static final String UUID_PLACEHOLDER = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
}
