package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotCorpusAssemblerTest {

    @Mock private NamedParameterJdbcTemplate namedJdbc;

    private SnapshotCorpusAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new SnapshotCorpusAssembler(namedJdbc);
    }

    @AfterEach
    void tearDown() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void assembleFullCorpusText_throwsWhenSnapshotListEmpty() {
        ExecutionContext ctx = ctxWithSnapshots(List.of());
        assertThatThrownBy(() -> assembler.assembleFullCorpusText(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void assembleFullCorpusText_returnsEmptyWhenHolderUnset() {
        RagExecutionContextHolder.clear();
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = ctxWithSnapshots(List.of(sid));
        assertThatThrownBy(() -> assembler.assembleFullCorpusText(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RagExecutionContextHolder");
    }

    @Test
    void assembleFullCorpusText_returnsEmptyWhenNoProjectScope() {
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        RagExecutionContextHolder.set(RagExecutionContext.forLegacyPipeline(cfg, "t"));
        ExecutionContext ctx = ctxWithSnapshots(List.of(UUID.randomUUID()));
        assertThat(assembler.assembleFullCorpusText(ctx)).isEmpty();
    }

    @Test
    void assembleFullCorpusText_returnsEmptyWhenProjectIdInvalid() {
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, "not-a-uuid", cfg, List.of(RagExecutionContext.ALL_DOCUMENTS), "t"));
        ExecutionContext ctx = ctxWithSnapshots(List.of(UUID.randomUUID()));
        assertThat(assembler.assembleFullCorpusText(ctx)).isEmpty();
    }

    @Test
    void assembleFullCorpusText_returnsEmptyWhenJdbcReturnsNoRows() {
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        UUID projectId = UUID.randomUUID();
        RagExecutionContextHolder.set(
                new RagExecutionContext(
                        null, null, projectId.toString(), cfg, List.of(RagExecutionContext.ALL_DOCUMENTS), "t"));
        ExecutionContext ctx = ctxWithSnapshots(List.of(UUID.randomUUID()));
        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        assertThat(assembler.assembleFullCorpusText(ctx)).isEmpty();
    }

    @Test
    void assembleFullCorpusText_concatenatesChunksFromJdbc() {
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        UUID projectId = UUID.randomUUID();
        RagExecutionContextHolder.set(
                new RagExecutionContext(
                        null, null, projectId.toString(), cfg, List.of(RagExecutionContext.ALL_DOCUMENTS), "t"));
        ExecutionContext ctx = ctxWithSnapshots(List.of(UUID.randomUUID()));
        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of("aaaa", "bbbbbbbb"));
        String text = assembler.assembleFullCorpusText(ctx);
        assertThat(text).contains("aaaa");
        assertThat(text).contains("bbbbbbbb");
    }

    private static ExecutionContext ctxWithSnapshots(List<UUID> ids) {
        var resolved = org.mockito.Mockito.mock(com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig.class);
        org.mockito.Mockito.lenient()
                .when(resolved.toRagConfig())
                .thenReturn(
                        RagConfig.fromFeatureConfiguration(
                                new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE"));
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                new KnowledgeSnapshotSelection(ids, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
