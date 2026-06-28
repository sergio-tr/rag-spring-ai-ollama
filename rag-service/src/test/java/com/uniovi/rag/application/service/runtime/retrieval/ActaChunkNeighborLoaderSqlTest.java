package com.uniovi.rag.application.service.runtime.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActaChunkNeighborLoaderSqlTest {

    @Test
    void loadNeighborChunks_usesPositionalParameters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(
                        contains("BETWEEN ? AND ?"),
                        any(RowMapper.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(List.of());

        ActaChunkNeighborLoader loader = new ActaChunkNeighborLoader(jdbc, new ObjectMapper());
        assertThatCode(
                        () ->
                                loader.loadNeighborChunks(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "doc-1",
                                        2,
                                        1))
                .doesNotThrowAnyException();
    }
}
