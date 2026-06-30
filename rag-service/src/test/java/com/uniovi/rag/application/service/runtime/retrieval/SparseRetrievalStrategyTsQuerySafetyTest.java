package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.SparseQueryPreparation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SparseRetrievalStrategyTsQuerySafetyTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private SparseQueryPreparer sparseQueryPreparer;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private SparseRetrievalStrategy strategy;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(transactionStatus.createSavepoint()).thenReturn(new Object());
        strategy =
                new SparseRetrievalStrategy(
                        jdbc,
                        sparseQueryPreparer,
                        new SparseDomainSynonyms(),
                        false,
                        transactionManager,
                        true);
    }

    @Test
    void sparseRetrievalSanitizesTimeTokenWithColon() {
        String orJoined =
                SparseTsQuerySanitizer.joinOrTerms(List.of("fechas", "terminaron", "tarde", "8:30"));

        assertThat(orJoined).doesNotContain(":");
        assertThat(orJoined).contains("8h30");
        assertThat(SparseTsQuerySanitizer.isValidOrTsquery(orJoined)).isTrue();
    }

    @Test
    void sparseRetrievalDoesNotAbortTransactionWhenTsqueryTokenIsInvalid() {
        UUID sid = UUID.randomUUID();
        stubEightThirtyPreparation();
        RetrievalRequest req = hybridRequest(sid, "dime las fechas de las actas que terminaron más tarde de las 8:30");

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataAccessException("syntax error in tsquery") {});

        Object savepoint = new Object();
        when(transactionStatus.createSavepoint()).thenReturn(savepoint);

        assertThatCode(() -> strategy.retrieve(req, null)).doesNotThrowAnyException();

        verify(transactionStatus, atLeastOnce()).rollbackToSavepoint(savepoint);
        verify(transactionStatus, atLeastOnce()).releaseSavepoint(savepoint);
    }

    @Test
    void queryWithEightThirtyReturnsControlledResultNotServerError() {
        UUID sid = UUID.randomUUID();
        stubEightThirtyPreparation();
        RetrievalRequest req = hybridRequest(sid, "dime las fechas de las actas que terminaron más tarde de las 8:30");

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        SparseRetrievalOutcome outcome = strategy.retrieve(req, null);

        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.telemetry().hit()).isFalse();
        assertThat(outcome.telemetry().noHitReason()).isEqualTo("no_lexical_match");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, atLeastOnce()).query(anyString(), paramCaptor.capture(), any(RowMapper.class));

        boolean safeOrQuerySeen =
                paramCaptor.getAllValues().stream()
                        .map(p -> p.getValue("query"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .anyMatch(q -> q.contains("8h30") && !q.contains("8:30"));
        assertThat(safeOrQuerySeen).isTrue();
    }

    @Test
    void invalidSparseQueryFallsBackWithoutLosingProviderContext() {
        UUID sid = UUID.randomUUID();
        stubEightThirtyPreparation();
        RetrievalRequest req = hybridRequest(sid, "dime las fechas de las actas que terminaron más tarde de las 8:30");

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataAccessException("syntax error in tsquery") {});

        SparseRetrievalOutcome outcome = strategy.retrieve(req, null);

        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.preparation().originalQuery()).contains("8:30");
        assertThat(outcome.telemetry().originalQuery()).contains("8:30");
        assertThat(outcome.telemetry().hit()).isFalse();
        assertThat(outcome.fallbackStage()).isNotNull();
        assertThat(req.mode()).isEqualTo(RetrievalMode.HYBRID_DENSE_SPARSE);
        assertThat(req.snapshotIds()).containsExactly(sid);
    }

    private void stubEightThirtyPreparation() {
        when(sparseQueryPreparer.prepare(anyString(), any()))
                .thenReturn(
                        new SparseQueryPreparation(
                                "dime las fechas de las actas que terminaron más tarde de las 8:30 de la tarde",
                                "dime las fechas de las actas que terminaron mas tarde de las 8:30 de la tarde",
                                List.of("fechas", "terminaron", "tarde", "8:30"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()));
    }

    private static RetrievalRequest hybridRequest(UUID sid, String query) {
        return new RetrievalRequest(
                query,
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
    }
}
