package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatExecutionTelemetryMapperRetrievalIdsTest {

    @Test
    void enrichRetrievedIdentifiersFromSources_populatesLabExportKeys() {
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("sourceCount", 1);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentId", "doc-1");
        source.put("projectDocumentId", "proj-doc-1");
        source.put("metadata", Map.of("chunkId", "snap:chunk:0"));

        ChatExecutionTelemetryMapper.enrichRetrievedIdentifiersFromSources(telemetry, List.of(source));

        assertThat(telemetry.get("retrieved_document_ids")).isEqualTo(List.of("doc-1"));
        assertThat(telemetry.get("retrieved_chunk_ids")).isEqualTo(List.of("snap:chunk:0"));
    }
}
