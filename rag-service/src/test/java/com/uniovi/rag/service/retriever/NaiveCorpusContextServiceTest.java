package com.uniovi.rag.service.retriever;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaiveCorpusContextServiceTest {

    @Mock
    private NamedParameterJdbcTemplate namedJdbc;

    @AfterEach
    void clearHolder() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void buildNaiveCorpusContextIfConfigured_returnsNull_whenDisabled() {
        RagExecutionContextHolder.set(RagExecutionContext.forLegacyPipeline(baseRag(false, 10_000), "t"));
        NaiveCorpusContextService s = new NaiveCorpusContextService(namedJdbc);

        assertThat(s.buildNaiveCorpusContextIfConfigured()).isNull();
        verifyNoInteractions(namedJdbc);
    }

    @Test
    void buildNaiveCorpusContextIfConfigured_returnsNull_whenNotProjectScoped() {
        RagConfig rag = baseRag(true, 10_000);
        RagExecutionContextHolder.set(new RagExecutionContext(null, null, null, rag, List.of("all"), "t"));
        NaiveCorpusContextService s = new NaiveCorpusContextService(namedJdbc);

        assertThat(s.buildNaiveCorpusContextIfConfigured()).isNull();
        verifyNoInteractions(namedJdbc);
    }

    @Test
    void buildNaiveCorpusContextIfConfigured_returnsEmpty_whenNoRows() {
        UUID pid = UUID.randomUUID();
        RagExecutionContextHolder.set(new RagExecutionContext(null, null, pid.toString(), baseRag(true, 10_000), List.of("all"), "t"));
        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        NaiveCorpusContextService s = new NaiveCorpusContextService(namedJdbc);

        assertThat(s.buildNaiveCorpusContextIfConfigured()).isEqualTo("");
    }

    @Test
    void buildNaiveCorpusContextIfConfigured_concatenatesAndCaps() {
        UUID pid = UUID.randomUUID();
        RagExecutionContextHolder.set(new RagExecutionContext(null, null, pid.toString(), baseRag(true, 30), List.of("all"), "t"));
        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of("  A  ", " ", "B", "C"));
        NaiveCorpusContextService s = new NaiveCorpusContextService(namedJdbc);

        String out = s.buildNaiveCorpusContextIfConfigured();
        assertThat(out).isNotNull();
        // Service enforces a minimum cap of 1024 characters regardless of config.
        assertThat(out.length()).isLessThanOrEqualTo(1024);
        assertThat(out).startsWith("A");
    }

    @Test
    void buildNaiveCorpusContextIfConfigured_usesDocAllowlistQuery_whenFiltered() {
        UUID pid = UUID.randomUUID();
        RagConfig rag = baseRag(true, 10_000);
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, pid.toString(), rag, List.of("d1", " ", "d2"), "t"));
        when(namedJdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of("X"));
        NaiveCorpusContextService s = new NaiveCorpusContextService(namedJdbc);

        assertThat(s.buildNaiveCorpusContextIfConfigured()).isEqualTo("X");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedJdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("metadata->>'document_id' IN (:docIds)");
        assertThat(params.getValue().getValues()).containsEntry("projectId", pid);
        assertThat(params.getValue().getValues().get("docIds")).asList().contains("d1", "d2");
    }

    private static RagConfig baseRag(boolean enabled, int maxChars) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                5,
                0.7,
                "l",
                "e",
                "c",
                "r",
                enabled,
                maxChars,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }
}

