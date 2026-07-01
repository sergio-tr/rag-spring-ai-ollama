package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SparseRetrievalSavepointRuntimeTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private SparseQueryPreparer sparseQueryPreparer;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SimpleTransactionStatus outerChatTransaction;

    @BeforeEach
    void setUp() {
        outerChatTransaction = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(
                        inv -> {
                            TransactionDefinition def = inv.getArgument(0);
                            if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                                return new SimpleTransactionStatus();
                            }
                            return outerChatTransaction;
                        });
    }

    @Test
    void sparseRetrievalWithoutSavepointSupportFallsBackWithoutAbortingChat() {
        SparseRetrievalStrategy strategy =
                new SparseRetrievalStrategy(
                        jdbc, sparseQueryPreparer, new SparseDomainSynonyms(), false, transactionManager, false);
        stubEightThirtyPreparation();
        RetrievalRequest req = hybridRequest(UUID.randomUUID());

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataAccessException("syntax error in tsquery") {});

        assertThatCode(() -> strategy.retrieve(req, null)).doesNotThrowAnyException();

        SparseRetrievalOutcome outcome = strategy.retrieve(req, null);
        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.telemetry().hit()).isFalse();
        assertThat(outerChatTransaction.isRollbackOnly()).isFalse();
        assertThat(outerChatTransaction.isCompleted()).isFalse();
    }

    @Test
    void sparseRetrievalSqlFailureDoesNotRollbackWholeChatTransaction() {
        SparseRetrievalStrategy strategy =
                new SparseRetrievalStrategy(
                        jdbc, sparseQueryPreparer, new SparseDomainSynonyms(), false, transactionManager, false);
        stubEightThirtyPreparation();
        RetrievalRequest req = hybridRequest(UUID.randomUUID());

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataAccessException("syntax error in tsquery") {});

        strategy.retrieve(req, null);

        assertThat(outerChatTransaction.isRollbackOnly())
                .as("chat transaction must stay committable after sparse SQL failure")
                .isFalse();
        verify(transactionManager, atLeastOnce()).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void timeQueryEightThirtySucceedsWithoutJpaSavepointSupport() {
        SparseRetrievalStrategy strategy =
                new SparseRetrievalStrategy(
                        jdbc, sparseQueryPreparer, new SparseDomainSynonyms(), false, transactionManager, false);
        stubEightThirtyPreparation();
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                hybridRequest(sid, "dime las fechas de las actas que terminaron más tarde de las 8:30 de la tarde");

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        SparseRetrievalOutcome outcome = strategy.retrieve(req, null);

        assertThat(outcome).isNotNull();
        assertThat(outcome.preparation().originalQuery()).contains("8:30");
        assertThat(outerChatTransaction.isRollbackOnly()).isFalse();
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

    private static RetrievalRequest hybridRequest(UUID sid) {
        return hybridRequest(sid, "dime las fechas de las actas que terminaron más tarde de las 8:30 de la tarde");
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
