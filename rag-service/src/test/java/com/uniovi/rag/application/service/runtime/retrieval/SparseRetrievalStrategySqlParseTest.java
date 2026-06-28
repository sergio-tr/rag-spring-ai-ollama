package com.uniovi.rag.application.service.runtime.retrieval;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;

import static org.assertj.core.api.Assertions.assertThat;

class SparseRetrievalStrategySqlParseTest {

  @Test
  void ilikeSql_namedParameterParsing_avoidsPostgresCastBindCollisions() {
    String sql =
        """
                SELECT id, content, CAST(metadata AS TEXT) AS metadata_json, chunk_index, 0.01 AS rank
                FROM vector_store
                WHERE project_id IS NOT DISTINCT FROM :projectId
                  AND metadata->>'indexSnapshotId' IS NOT NULL
                  AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                 AND (lower(content) LIKE :pattern0 OR lower(content) LIKE :pattern1)
                LIMIT :limit
                """;

    ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
    String substituted =
        NamedParameterUtils.substituteNamedParameters(
            parsed,
            new MapSqlParameterSource()
                .addValue("projectId", "p")
                .addValue("snapshotIds", List.of("s"))
                .addValue("pattern0", "%a%")
                .addValue("pattern1", "%b%")
                .addValue("limit", 5));

    assertThat(substituted).contains("CAST(metadata AS TEXT)");
    assertThat(substituted).doesNotContain(":uuid");
    assertThat(substituted).doesNotContain(":text");
    assertThat(substituted).contains("LIKE ?");
  }
}
