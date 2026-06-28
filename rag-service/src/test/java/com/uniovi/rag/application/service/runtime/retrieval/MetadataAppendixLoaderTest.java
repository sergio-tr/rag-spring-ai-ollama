package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MetadataAppendixLoader} (wave 6.05 JaCoCo recovery).
 */
@ExtendWith(MockitoExtension.class)
class MetadataAppendixLoaderTest {

    @Mock
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void loadAppendix_returnsEmpty_whenProjectIdMissing() {
        MetadataAppendixLoader loader = new MetadataAppendixLoader(namedJdbc);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.projectId()).thenReturn(null);
        when(ctx.knowledgeSnapshotSelection())
                .thenReturn(new KnowledgeSnapshotSelection(List.of(UUID.randomUUID()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        String out =
                loader.loadAppendix(
                        ctx,
                        mock(QueryPlan.class),
                        List.of(candidate(UUID.randomUUID(), "d1")));

        assertThat(out).isEmpty();
        verifyNoInteractions(namedJdbc);
    }

    @Test
    void loadAppendix_returnsEmpty_whenNoSnapshots() {
        MetadataAppendixLoader loader = new MetadataAppendixLoader(namedJdbc);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.projectId()).thenReturn(UUID.randomUUID());
        when(ctx.knowledgeSnapshotSelection()).thenReturn(KnowledgeSnapshotSelection.empty());

        String out =
                loader.loadAppendix(ctx, mock(QueryPlan.class), List.of(candidate(UUID.randomUUID(), "d1")));

        assertThat(out).isEmpty();
        verifyNoInteractions(namedJdbc);
    }

    @Test
    void loadAppendix_returnsEmpty_whenSurvivorsHaveNoParsableDocumentIds() {
        MetadataAppendixLoader loader = new MetadataAppendixLoader(namedJdbc);
        UUID projectId = UUID.randomUUID();
        UUID snap = UUID.randomUUID();
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.projectId()).thenReturn(projectId);
        when(ctx.knowledgeSnapshotSelection())
                .thenReturn(new KnowledgeSnapshotSelection(List.of(snap), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        RetrievalCandidate bad =
                new RetrievalCandidate("x", "t", Map.of("document_id", "not-a-uuid"), 0, 0, 1, 0, snap, 0);

        String out = loader.loadAppendix(ctx, mock(QueryPlan.class), List.of(bad));

        assertThat(out).isEmpty();
        verifyNoInteractions(namedJdbc);
    }

    @Test
    void loadAppendix_formatsActaMetadataWithDistinctPrefix() {
        MetadataAppendixLoader loader = new MetadataAppendixLoader(namedJdbc);
        UUID projectId = UUID.randomUUID();
        UUID snap = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.projectId()).thenReturn(projectId);
        when(ctx.knowledgeSnapshotSelection())
                .thenReturn(new KnowledgeSnapshotSelection(List.of(snap), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of("{\"president\":\"Juan Pérez\",\"date_iso\":\"2025-02-24\"}"));

        String out =
                loader.loadAppendix(
                        ctx,
                        mock(QueryPlan.class),
                        List.of(candidate(snap, docId.toString())));

        assertThat(out).startsWith("[acta-metadata] ");
        assertThat(out).contains("Juan Pérez");
    }

    @Test
    void loadAppendix_queriesAndFormatsPayloads() {
        MetadataAppendixLoader loader = new MetadataAppendixLoader(namedJdbc);
        UUID projectId = UUID.randomUUID();
        UUID snap = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.projectId()).thenReturn(projectId);
        when(ctx.knowledgeSnapshotSelection())
                .thenReturn(new KnowledgeSnapshotSelection(List.of(snap), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of("{\"k\":1}", "   ", "{\"k\":2}"));

        String out =
                loader.loadAppendix(
                        ctx,
                        mock(QueryPlan.class),
                        List.of(candidate(snap, docId.toString())));

        assertThat(out)
                .isEqualTo(
                        "[metadata] {\"k\":1}\n"
                                + "[metadata] {\"k\":2}");
    }

    private static RetrievalCandidate candidate(UUID snapshotId, String documentId) {
        return new RetrievalCandidate(
                "id",
                "c",
                Map.of("document_id", documentId, "chunk_index", 0),
                0,
                0,
                1,
                0,
                snapshotId,
                0);
    }
}
