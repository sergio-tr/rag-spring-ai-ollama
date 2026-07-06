package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.service.knowledge.document.ActaSectionChunk;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataRetrievalBoosterTest {

    @Test
    void boostsParticipantSectionForAttendeeQuery() {
        RetrievalCandidate header =
                candidate("ACTA 3.pdf", ActaSectionChunk.SECTION_HEADER, "header body", 0.5);
        RetrievalCandidate participants =
                candidate("ACTA 3.pdf", ActaSectionChunk.SECTION_PARTICIPANTS, "lista de propietarios", 0.4);

        List<RetrievalCandidate> out =
                MetadataRetrievalBooster.apply(
                        request("cuales son los asistentes del acta del 25 de agosto del 2025"),
                        minimalPlan(),
                        AdvancedRetrievalPipeline.WORKFLOW_CHUNK_DENSE_METADATA,
                        List.of(header, participants));

        assertThat(out.getFirst().metadata().get("sectionType")).isEqualTo(ActaSectionChunk.SECTION_PARTICIPANTS);
        assertThat(out.getFirst().fusedRrfScore()).isGreaterThan(header.fusedRrfScore());
    }

    @Test
    void noOpForNonMetadataWorkflow() {
        RetrievalCandidate low = candidate("ACTA 3.pdf", ActaSectionChunk.SECTION_BODY, "x", 0.2);
        RetrievalCandidate high = candidate("ACTA 3.pdf", ActaSectionChunk.SECTION_HEADER, "y", 0.9);

        List<RetrievalCandidate> out =
                MetadataRetrievalBooster.apply(
                        request("cuales son los asistentes del acta del 25 de agosto del 2025"),
                        minimalPlan(),
                        "ChunkDenseRagWorkflow",
                        List.of(low, high));

        assertThat(out).containsExactly(low, high);
    }

    @Test
    void boostsTopicOverlapForSecurityCameraQuery() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filename", "ACTA 1.pdf");
        meta.put("topics", List.of("cámaras de seguridad", "videovigilancia"));
        meta.put("sectionType", ActaSectionChunk.SECTION_AGENDA);
        RetrievalCandidate match = new RetrievalCandidate("a", "agenda", meta, 0.1, 0.1, 1, 1, UUID.randomUUID(), 0.3);
        RetrievalCandidate other = candidate("ACTA 2.pdf", ActaSectionChunk.SECTION_BODY, "other", 0.35);

        List<RetrievalCandidate> out =
                MetadataRetrievalBooster.apply(
                        request("en qué actas se habla sobre cámaras de seguridad"),
                        minimalPlan(),
                        AdvancedRetrievalPipeline.WORKFLOW_CHUNK_DENSE_METADATA,
                        List.of(other, match));

        assertThat(out.getFirst().candidateId()).isEqualTo("a");
    }

    private static RetrievalRequest request(String query) {
        return new RetrievalRequest(
                query,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote("test"),
                RetrievalMode.DENSE_ONLY,
                8,
                8,
                16,
                8,
                24_000,
                16,
                List.of(UUID.randomUUID()),
                UUID.randomUUID(),
                Optional.empty(),
                List.of(),
                true,
                Optional.of("embedding"));
    }

    private static QueryPlan minimalPlan() {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                "q",
                "q",
                "q",
                "q",
                "label",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.LIST,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote("test"),
                StructuredRewriteResult.identityDisabled("q", null),
                ExpectedAnswerShape.LIST,
                AmbiguityAssessment.sufficient(),
                "corr",
                "m",
                List.of());
    }

    private static RetrievalCandidate candidate(
            String filename, String sectionType, String content, double score) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filename", filename);
        meta.put("sectionType", sectionType);
        return new RetrievalCandidate(
                filename + ":" + sectionType,
                content,
                meta,
                0.1,
                0.1,
                1,
                1,
                UUID.randomUUID(),
                score);
    }
}
