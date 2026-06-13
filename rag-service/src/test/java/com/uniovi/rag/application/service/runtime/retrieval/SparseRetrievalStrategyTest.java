package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparseRetrievalStrategyTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private SparseRetrievalStrategy sparseRetrievalStrategy;

    @Test
    void retrieve_wrapsJdbcFailureAsRagServiceException() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"), true, Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataAccessException("simulated") {});

        assertThatThrownBy(() -> sparseRetrievalStrategy.retrieve(req))
                .asInstanceOf(type(RagServiceException.class))
                .extracting(RagServiceException::getPublicMessage)
                .isEqualTo("hybrid sparse retrieval failed");
    }

    @Test
    void retrieve_sqlUsesContentTsvAndWebsearch() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "budget acta",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"), true, Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        sparseRetrievalStrategy.retrieve(req);

        Mockito.verify(jdbc)
                .query(
                        ArgumentMatchers.argThat(
                                (String sql) ->
                                        sql.contains("content_tsv")
                                                && sql.contains("websearch_to_tsquery")
                                                && sql.contains("ts_rank_cd")),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class));
    }

    @Test
    void retrieve_returnsEmpty_whenAllowlistExcludesAllDocumentsAfterParsing() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all", "  ", "ALL"), false, Optional.empty());

        List<RetrievalCandidate> out = sparseRetrievalStrategy.retrieve(req);

        assertThat(out).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void retrieve_appendsDocumentFilterSqlWhenAllowlistIsRestricted() {
        UUID sid = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of(docId.toString()),
                        false,
                        Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        sparseRetrievalStrategy.retrieve(req);

        verify(jdbc, Mockito.times(2))
                .query(
                        ArgumentMatchers.argThat(
                                (String sql) -> sql.contains("(metadata->>'document_id')::uuid IN (:docIds)")),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class));
    }

    @Test
    void retrieve_fallsBackToPlaintoTsqueryWhenWebsearchReturnsEmpty() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "probe token",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"),
                        true,
                        Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        sparseRetrievalStrategy.retrieve(req);

        verify(jdbc)
                .query(
                        ArgumentMatchers.argThat((String sql) -> sql.contains("websearch_to_tsquery")),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class));
        verify(jdbc)
                .query(
                        ArgumentMatchers.argThat((String sql) -> sql.contains("plainto_tsquery")),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class));
    }

    @Test
    void retrieve_mapsJdbcRowToCandidate() throws Exception {
        UUID sid = UUID.randomUUID();
        UUID docUuid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "search terms",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"), true, Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            RowMapper<RetrievalCandidate> rm = inv.getArgument(2, RowMapper.class);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getString("metadata_json"))
                                    .thenReturn(
                                            "{\"indexSnapshotId\":\""
                                                    + sid
                                                    + "\",\"document_id\":\""
                                                    + docUuid
                                                    + "\",\"chunk_index\":0}");
                            when(rs.getString("content")).thenReturn("chunk body");
                            when(rs.getObject("chunk_index")).thenReturn(0);
                            when(rs.wasNull()).thenReturn(false);
                            when(rs.getDouble("rank")).thenReturn(1.25);
                            return List.of(rm.mapRow(rs, 0));
                        });

        List<RetrievalCandidate> out = sparseRetrievalStrategy.retrieve(req);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().content()).isEqualTo("chunk body");
        assertThat(out.getFirst().snapshotId()).isEqualTo(sid);
        assertThat(out.getFirst().sparseScore()).isEqualTo(1.25);
    }

    @Test
    void retrieve_filtersRowsWhenSnapshotIdMissingAfterParse() throws Exception {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"), true, Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            RowMapper<RetrievalCandidate> rm = inv.getArgument(2, RowMapper.class);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getString("metadata_json"))
                                    .thenReturn("{\"document_id\":\"" + UUID.randomUUID() + "\"}");
                            return Collections.singletonList(rm.mapRow(rs, 0));
                        });

        List<RetrievalCandidate> out = sparseRetrievalStrategy.retrieve(req);

        assertThat(out).isEmpty();
    }

    @Test
    void retrieve_wrapsMalformedMetadataJsonFromRow() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"), true, Optional.empty());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            RowMapper<RetrievalCandidate> rm = inv.getArgument(2, RowMapper.class);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getString("metadata_json")).thenReturn("{not-json");
                            return List.of(rm.mapRow(rs, 0));
                        });

        assertThatThrownBy(() -> sparseRetrievalStrategy.retrieve(req))
                .asInstanceOf(type(RagServiceException.class))
                .extracting(RagServiceException::getPublicMessage)
                .isEqualTo("hybrid sparse retrieval failed");
    }
}
