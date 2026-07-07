package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.knowledge.document.ActaSectionChunk;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalContextExpanderTest {

    private ActaChunkNeighborLoader neighborLoader;
    private RetrievalContextExpander expander;

    @BeforeEach
    void setUp() {
        neighborLoader = mock(ActaChunkNeighborLoader.class);
        expander = new RetrievalContextExpander(neighborLoader);
    }

    @Test
    void expand_countQuery_deduplicatesByDocument() {
        UUID snap = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate c1 =
                new RetrievalCandidate("c1", "a", Map.of("projectDocumentId", doc, "chunkIndex", 0), 0, 0, 0, 0, snap, 0.9);
        RetrievalCandidate c2 =
                new RetrievalCandidate("c2", "b", Map.of("projectDocumentId", doc, "chunkIndex", 1), 0, 0, 0, 0, snap, 0.5);

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.COUNT);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.SCALAR_COUNT);

        RetrievalRequest req = mock(RetrievalRequest.class);
        var result = expander.expand(req, plan, List.of(c1, c2));

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().fusedRrfScore()).isEqualTo(0.9);
        assertThat(result.notes()).anyMatch(n -> n.startsWith("count_dedupe:"));
        verify(neighborLoader, never()).loadSectionSiblings(any(), any(), any(), any(), anyInt());
    }

    @Test
    void expand_scopedAttendeeCountQuery_loadsHeaderSection() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate participantsHit =
                new RetrievalCandidate(
                        "p1",
                        "Beatriz Suárez Aguilar (Presidente)",
                        Map.of(
                                "projectDocumentId",
                                doc,
                                "sectionType",
                                ActaSectionChunk.SECTION_PARTICIPANTS,
                                "chunkIndex",
                                1),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate header =
                new RetrievalCandidate(
                        "h1",
                        "Asistentes: 18 propietarios",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_PARTICIPANTS), anyInt()))
                .thenReturn(List.of(participantsHit));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_HEADER), anyInt()))
                .thenReturn(List.of(header));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.COUNT);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.SCALAR_COUNT);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText())
                .thenReturn("¿Cuántos propietarios asistieron a la reunión del 25 de agosto de 2025 (ACTA 3.pdf)?");
        when(req.postFusionCap()).thenReturn(8);

        var result = expander.expand(req, plan, List.of(participantsHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("h1");
        assertThat(RetrievalContextExpander.isScopedAttendeeCountQuery(req.queryText())).isTrue();
    }

    @Test
    void isScopedAttendeeCountQuery_falseForCorpusWideCount() {
        assertThat(RetrievalContextExpander.isScopedAttendeeCountQuery("¿Cuántos propietarios asistieron en total?"))
                .isFalse();
    }

    @Test
    void expand_listQuery_mergesSectionSiblingsInBatch() {
        UUID snap = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate part0 =
                new RetrievalCandidate(
                        "p0",
                        "• Ana",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_PARTICIPANTS, "sectionPart", 0, "chunkIndex", 1),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate part1 =
                new RetrievalCandidate(
                        "p1",
                        "• Luis",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_PARTICIPANTS, "sectionPart", 1, "chunkIndex", 2),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.7);

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.LIST);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.LIST);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(null);

        var result = expander.expand(req, plan, List.of(part0, part1));

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().content()).contains("Ana").contains("Luis");
        assertThat(result.candidates().getFirst().metadata().get("sectionExpanded")).isEqualTo(true);
    }

    @Test
    void expand_summaryQuery_loadsHeaderNeighborsFromStore() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate agendaHit =
                new RetrievalCandidate(
                        "a1",
                        "Orden del día",
                        Map.of(
                                "projectDocumentId",
                                doc,
                                "sectionType",
                                ActaSectionChunk.SECTION_AGENDA,
                                "chunkIndex",
                                2,
                                "actaDate",
                                "2026-02-25"),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate header =
                new RetrievalCandidate(
                        "h1",
                        "Fecha: 25 de febrero de 2026",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_AGENDA), anyInt()))
                .thenReturn(List.of(agendaHit));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_HEADER), anyInt()))
                .thenReturn(List.of(header));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.SUMMARIZE);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.SUMMARY);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText()).thenReturn("Resume el acta del 25/02/2026");

        var result = expander.expand(req, plan, List.of(agendaHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("h1");
    }

    @Test
    void expand_listQuery_mergesEightChunksIntoThreeSectionCandidates() {
        UUID snap = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        List<RetrievalCandidate> seeds =
                List.of(
                        sectionCandidate("p0", "Ana", doc, ActaSectionChunk.SECTION_PARTICIPANTS, 0, snap, 0.95),
                        sectionCandidate("p1", "Luis", doc, ActaSectionChunk.SECTION_PARTICIPANTS, 1, snap, 0.90),
                        sectionCandidate("p2", "Marta", doc, ActaSectionChunk.SECTION_PARTICIPANTS, 2, snap, 0.85),
                        sectionCandidate("a0", "Punto 1", doc, ActaSectionChunk.SECTION_AGENDA, 3, snap, 0.80),
                        sectionCandidate("a1", "Punto 2", doc, ActaSectionChunk.SECTION_AGENDA, 4, snap, 0.75),
                        sectionCandidate("a2", "Punto 3", doc, ActaSectionChunk.SECTION_AGENDA, 5, snap, 0.70),
                        sectionCandidate("s0", "Se aprueba", doc, ActaSectionChunk.SECTION_AGREEMENTS, 6, snap, 0.65),
                        sectionCandidate("s1", "Se cierra", doc, ActaSectionChunk.SECTION_AGREEMENTS, 7, snap, 0.60));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.LIST);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.LIST);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(null);
        when(req.postFusionCap()).thenReturn(8);

        var result = expander.expand(req, plan, seeds);

        assertThat(result.candidates()).hasSize(3);
        assertThat(result.notes()).anyMatch(n -> n.startsWith("section_expand:8->3"));
    }

    @Test
    void expand_scopedAttendeeListQuery_loadsParticipantsSection() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate headerHit =
                new RetrievalCandidate(
                        "h0",
                        "Fecha: 25 de febrero de 2025",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate participants =
                new RetrievalCandidate(
                        "p0",
                        "• Ana\n• Luis",
                        Map.of(
                                "projectDocumentId",
                                doc,
                                "sectionType",
                                ActaSectionChunk.SECTION_PARTICIPANTS,
                                "chunkIndex",
                                1),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_PARTICIPANTS), anyInt()))
                .thenReturn(List.of(participants));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_HEADER), anyInt()))
                .thenReturn(List.of(headerHit));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.LIST);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.LIST);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText()).thenReturn("cuales son los asistentes del acta del 25 de febrero del 2025?");
        when(req.postFusionCap()).thenReturn(8);

        var result = expander.expand(req, plan, List.of(headerHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("p0");
    }

    @Test
    void expand_decisionQuery_loadsAgendaSection() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate headerHit =
                new RetrievalCandidate(
                        "h0",
                        "Fecha: 25 de agosto de 2025",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate agenda =
                new RetrievalCandidate(
                        "a0",
                        "Se acordó instalar cámaras",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_AGENDA, "chunkIndex", 3),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_HEADER), anyInt()))
                .thenReturn(List.of(headerHit));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_AGENDA), anyInt()))
                .thenReturn(List.of(agenda));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.FIND);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.PARAGRAPH);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText()).thenReturn("qué decisiones se tomaron en el acta del 25 de agosto de 2025?");
        when(req.postFusionCap()).thenReturn(8);

        var result = expander.expand(req, plan, List.of(headerHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("a0");
    }

    @Test
    void expand_decisionQuery_loadsClosingSection() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate headerHit =
                new RetrievalCandidate(
                        "h0",
                        "Fecha: 25 de agosto de 2025",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate closing =
                new RetrievalCandidate(
                        "c0",
                        "Se acordó por unanimidad",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_CLOSING, "chunkIndex", 5),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_AGENDA), anyInt()))
                .thenReturn(List.of());
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_CLOSING), anyInt()))
                .thenReturn(List.of(closing));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.FIND);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText()).thenReturn("qué decisiones se tomaron en el acta del 25 de agosto de 2025?");

        var result = expander.expand(req, plan, List.of(headerHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("c0");
    }

    @Test
    void expand_topicQuery_loadsBodySection() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate headerHit =
                new RetrievalCandidate(
                        "h0",
                        "ACTA 1",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate body =
                new RetrievalCandidate(
                        "b0",
                        "Instalación de cámaras de videovigilancia",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_BODY, "chunkIndex", 4),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_BODY), anyInt()))
                .thenReturn(List.of(body));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_AGENDA), anyInt()))
                .thenReturn(List.of());

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.FIND);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText()).thenReturn("en qué actas se habla sobre cámaras de seguridad");

        var result = expander.expand(req, plan, List.of(headerHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("b0");
    }

    @Test
    void expand_q10Summary_loadsAgendaBodyAndClosing() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        RetrievalCandidate headerHit =
                new RetrievalCandidate(
                        "h0",
                        "ACTA 3",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.8);
        RetrievalCandidate agenda =
                new RetrievalCandidate(
                        "a0",
                        "Punto 1: presupuesto",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_AGENDA, "chunkIndex", 2),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_AGENDA), anyInt()))
                .thenReturn(List.of(agenda));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_BODY), anyInt()))
                .thenReturn(List.of());
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_CLOSING), anyInt()))
                .thenReturn(List.of());

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.SUMMARIZE);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText()).thenReturn("resume los puntos tratados en ACTA 3");

        var result = expander.expand(req, plan, List.of(headerHit));

        assertThat(result.candidates()).extracting(RetrievalCandidate::candidateId).contains("a0");
    }

    @Test
    void expand_scopedAttendeeCountQuery_preservesParticipantsThroughPostFusionCap() {
        UUID snap = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String doc = UUID.randomUUID().toString();
        List<RetrievalCandidate> noise =
                IntStream.range(0, 10)
                        .mapToObj(
                                i ->
                                        new RetrievalCandidate(
                                                "n" + i,
                                                "noise-" + i,
                                                Map.of("projectDocumentId", UUID.randomUUID().toString(), "chunkIndex", i),
                                                0,
                                                0,
                                                0,
                                                0,
                                                snap,
                                                1.0 - i * 0.05))
                        .toList();
        RetrievalCandidate headerHit =
                new RetrievalCandidate(
                        "h0",
                        "Fecha: 25 de agosto de 2025",
                        Map.of("projectDocumentId", doc, "sectionType", ActaSectionChunk.SECTION_HEADER, "chunkIndex", 0),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0.55);
        RetrievalCandidate participants =
                new RetrievalCandidate(
                        "p0",
                        "Asistentes: 18 propietarios",
                        Map.of(
                                "projectDocumentId",
                                doc,
                                "sectionType",
                                ActaSectionChunk.SECTION_PARTICIPANTS,
                                "chunkIndex",
                                1),
                        0,
                        0,
                        0,
                        0,
                        snap,
                        0);

        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_PARTICIPANTS), anyInt()))
                .thenReturn(List.of(participants));
        when(neighborLoader.loadSectionSiblings(
                        eq(projectId), eq(snap), eq(doc), eq(ActaSectionChunk.SECTION_HEADER), anyInt()))
                .thenReturn(List.of(headerHit));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.COUNT);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.SCALAR_COUNT);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(projectId);
        when(req.queryText())
                .thenReturn("¿Cuántos propietarios asistieron a la reunión del 25 de agosto de 2025 (ACTA 3.pdf)?");
        when(req.postFusionCap()).thenReturn(8);

        List<RetrievalCandidate> seeds = new ArrayList<>();
        seeds.add(headerHit);
        seeds.addAll(noise);

        var result = expander.expand(req, plan, seeds);

        assertThat(result.candidates()).anyMatch(c -> c.content().contains("18 propietarios"));
        assertThat(result.notes()).anyMatch(n -> n.startsWith("post_fusion_cap:"));
    }

    @Test
    void expand_appliesPostFusionCap_whenSeedsExceedCap() {
        UUID snap = UUID.randomUUID();
        List<RetrievalCandidate> seeds =
                List.of(
                        candidate("c0", snap, 1.0),
                        candidate("c1", snap, 0.95),
                        candidate("c2", snap, 0.90),
                        candidate("c3", snap, 0.85),
                        candidate("c4", snap, 0.80),
                        candidate("c5", snap, 0.75),
                        candidate("c6", snap, 0.70),
                        candidate("c7", snap, 0.65),
                        candidate("c8", snap, 0.60),
                        candidate("c9", snap, 0.55));

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.queryIntent()).thenReturn(QueryIntent.FIND);
        when(plan.expectedAnswerShape()).thenReturn(ExpectedAnswerShape.UNKNOWN);

        RetrievalRequest req = mock(RetrievalRequest.class);
        when(req.projectId()).thenReturn(null);
        when(req.postFusionCap()).thenReturn(8);
        when(req.queryText()).thenReturn("buscar acuerdos");

        var result = expander.expand(req, plan, seeds);

        assertThat(result.candidates()).hasSize(8);
        assertThat(result.notes()).anyMatch(n -> n.startsWith("post_fusion_cap:10->8"));
    }

    private static RetrievalCandidate sectionCandidate(
            String id,
            String content,
            String doc,
            String sectionType,
            int chunkIndex,
            UUID snap,
            double score) {
        return new RetrievalCandidate(
                id,
                content,
                Map.of("projectDocumentId", doc, "sectionType", sectionType, "chunkIndex", chunkIndex),
                0,
                0,
                0,
                0,
                snap,
                score);
    }

    private static RetrievalCandidate candidate(String id, UUID snap, double score) {
        return new RetrievalCandidate(
                id,
                "content-" + id,
                Map.of("chunkIndex", Integer.parseInt(id.substring(1))),
                0,
                0,
                0,
                0,
                snap,
                score);
    }
}
