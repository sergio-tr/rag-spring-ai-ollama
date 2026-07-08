package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.knowledge.document.ActaSectionChunk;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActaSectionContextPolicyTest {

    @Test
    void needsParticipantsExpansion_trueForScopedAttendeeList() {
        assertThat(
                        ActaSectionContextPolicy.needsParticipantsExpansion(
                                "cuales son los asistentes del acta del 25 de febrero del 2025?"))
                .isTrue();
    }

    @Test
    void needsParticipantsExpansion_falseForCorpusWideCount() {
        assertThat(ActaSectionContextPolicy.needsParticipantsExpansion("cuántas actas mencionan asistentes o propietarios?"))
                .isFalse();
    }

    @Test
    void needsAgendaExpansion_trueForDecisionQuery() {
        assertThat(
                        ActaSectionContextPolicy.needsAgendaExpansion(
                                "qué decisiones se tomaron en el acta del 25 de agosto de 2025?"))
                .isTrue();
    }

    @Test
    void needsBodyTopicExpansion_trueForCameraTopicQuery() {
        assertThat(ActaSectionContextPolicy.needsBodyTopicExpansion("en qué actas se habla sobre cámaras de seguridad"))
                .isTrue();
    }

    @Test
    void sectionRerankAdjustment_penalizesHeaderForParticipantList() {
        UUID snap = UUID.randomUUID();
        RetrievalCandidate header =
                new RetrievalCandidate(
                        "h",
                        "Fecha: 25 de febrero de 2025",
                        Map.of("sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.9);
        RetrievalCandidate participants =
                new RetrievalCandidate(
                        "p",
                        "• Ana\n• Luis",
                        Map.of("sectionType", ActaSectionChunk.SECTION_PARTICIPANTS, "chunkIndex", 1),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.5);

        String query = "cuales son los asistentes del acta del 25 de febrero del 2025?";
        assertThat(ActaSectionContextPolicy.sectionRerankAdjustment(query, header)).isNegative();
        assertThat(ActaSectionContextPolicy.sectionRerankAdjustment(query, participants)).isPositive();
    }

    @Test
    void isProtectedFromCompression_protectsAgendaForDecisionQuery() {
        UUID snap = UUID.randomUUID();
        RetrievalCandidate agenda =
                new RetrievalCandidate(
                        "a",
                        "Se acordó contratar cámaras",
                        Map.of("sectionType", ActaSectionChunk.SECTION_AGENDA, "chunkIndex", 3),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.5);
        assertThat(
                        ActaSectionContextPolicy.isProtectedFromCompression(
                                "qué decisiones se tomaron en el acta del 25 de agosto de 2025?", agenda))
                .isTrue();
    }
}
