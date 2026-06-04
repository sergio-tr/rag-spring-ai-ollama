package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRetrievedSourceMapperTest {

    @Test
    void marksNonSupportingSourcesOnGlobalDateMismatch() {
        RetrievalCandidate wrongYear = candidate("ACTA2.pdf", Map.of("date_iso", "2025-02-25"));
        DateGroundingSupport.DateGroundingDecision decision =
                DateGroundingSupport.decision("acta del 25/02/2026", List.of(wrongYear));

        List<ChatSource> sources =
                RuntimeRetrievedSourceMapper.toChatSources(
                        List.of(wrongYear),
                        Optional.of(decision.requestedDate()),
                        decision);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).detectedDate()).isEqualTo("2025-02-25");
        assertThat(sources.get(0).metadata()).containsEntry("supportingAnswer", false).containsEntry("alternativeOnly", true);
    }

    @Test
    void T_M5_BE_sourcesMap_exposesChunkIdInMetadata() {
        RetrievalCandidate exact = candidate("ACTA5.pdf", Map.of("date_iso", "2026-02-25", "chunkIndex", 2));
        DateGroundingSupport.DateGroundingDecision decision =
                DateGroundingSupport.decision("acta del 25/02/2026", List.of(exact));

        List<ChatSource> sources =
                RuntimeRetrievedSourceMapper.toChatSources(
                        List.of(exact), Optional.of(decision.requestedDate()), decision);

        assertThat(sources.get(0).chunkIndex()).isEqualTo(2);
        assertThat(sources.get(0).metadata()).containsEntry("chunkId", exact.candidateId());
    }

    @Test
    void marksExactDateSourceAsSupporting() {
        RetrievalCandidate exact = candidate("ACTA5.pdf", Map.of("date_iso", "2026-02-25"));
        DateGroundingSupport.DateGroundingDecision decision =
                DateGroundingSupport.decision("acta del 25/02/2026", List.of(exact));

        List<ChatSource> sources =
                RuntimeRetrievedSourceMapper.toChatSources(
                        List.of(exact), Optional.of(decision.requestedDate()), decision);

        assertThat(sources.get(0).metadata()).containsEntry("supportingAnswer", true);
    }

    private static RetrievalCandidate candidate(String filename, Map<String, Object> metadata) {
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("filename", filename);
        return new RetrievalCandidate(
                "chunk-" + filename, "body", meta, 0.1, 0.9, 1, 0, UUID.randomUUID(), 1.0);
    }
}
