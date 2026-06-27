package com.uniovi.rag.application.service.runtime.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;

import static org.assertj.core.api.Assertions.assertThat;

class ActaChunkNeighborLoaderSqlParseTest {

  private static final String CHUNK_INDEX_EXPR =
      "COALESCE(chunk_index, CAST(metadata->>'chunkIndex' AS INTEGER), 0)";

  @Test
  void neighborSql_namedParameterParsing_doesNotTreatChunkIndexJsonKeyAsBindName() {
    String sql =
        """
                SELECT content, CAST(metadata AS TEXT) AS metadata_json,
                       %s AS chunk_index
                FROM vector_store
                WHERE project_id = :projectId
                  AND metadata->>'indexSnapshotId' = :snapshotId
                  AND metadata->>'projectDocumentId' = :projectDocumentId
                  AND %s BETWEEN :minIdx AND :maxIdx
                ORDER BY %s, id
                """
            .formatted(CHUNK_INDEX_EXPR, CHUNK_INDEX_EXPR, CHUNK_INDEX_EXPR);

    ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
    String substituted =
        NamedParameterUtils.substituteNamedParameters(
            parsed,
            new MapSqlParameterSource()
                .addValue("projectId", "p")
                .addValue("snapshotId", "s")
                .addValue("projectDocumentId", "d")
                .addValue("minIdx", 0)
                .addValue("maxIdx", 2));

    assertThat(substituted).contains("CAST(metadata->>'chunkIndex' AS INTEGER)");
    assertThat(substituted).doesNotContain(":projectId");
    assertThat(substituted).contains("BETWEEN ? AND ?");
  }
}
