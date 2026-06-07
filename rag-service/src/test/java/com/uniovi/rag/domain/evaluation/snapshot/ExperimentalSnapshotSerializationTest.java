package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentalSnapshotSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void llmExperimentalSnapshot_roundTrip_preservesUnsupportedFields() throws Exception {
        LlmExperimentalSnapshot orig =
                new LlmExperimentalSnapshot(
                        "mistral:7b",
                        0.1,
                        0.95,
                        40,
                        0.05,
                        1.08,
                        4096,
                        256,
                        null,
                        123,
                        List.of("</s>"),
                        "json",
                        false,
                        List.of("minP"));
        String json = mapper.writeValueAsString(orig);
        LlmExperimentalSnapshot back = mapper.readValue(json, LlmExperimentalSnapshot.class);
        assertThat(back).isEqualTo(orig);
    }

    @Test
    void embeddingExperimentalSnapshot_roundTrip() throws Exception {
        EmbeddingExperimentalSnapshot orig =
                new EmbeddingExperimentalSnapshot(
                        "mxbai", 1024, true, "q:", "p:", 32, "truncate_tail", List.of("normalize_runtime"));
        String json = mapper.writeValueAsString(orig);
        assertThat(mapper.readValue(json, EmbeddingExperimentalSnapshot.class)).isEqualTo(orig);
    }

    @Test
    void promptProfileSnapshot_serializesRetrievalQuAlias() throws Exception {
        PromptProfileSnapshot p =
                new PromptProfileSnapshot(
                        "v1",
                        "base",
                        "proj",
                        "",
                        "Question: {{question}}",
                        "fmt",
                        "effective",
                        "deadbeef");
        JsonNode node = mapper.valueToTree(p);
        assertThat(node.has("retrieval_qu")).isTrue();
        assertThat(node.get("retrieval_qu").asText()).contains("{{question}}");
    }
}
