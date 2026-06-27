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
}
