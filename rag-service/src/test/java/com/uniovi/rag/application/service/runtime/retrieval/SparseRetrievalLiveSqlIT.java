package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport.PostgresBinding;
import com.uniovi.rag.testsupport.TestEnvironment;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;

@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isJdbcIntegrationTestAvailable",
        disabledReason = "Start Postgres or set INTEGRATION_JDBC_URL")
class SparseRetrievalLiveSqlIT {

    private static PostgresBinding postgresBinding;

    @BeforeAll
    static void bindDatabase() {
        postgresBinding = PostgresIntegrationTestSupport.startJdbcIntegrationDatabase();
    }

    @AfterAll
    static void releaseDatabase() {
        if (postgresBinding != null) {
            postgresBinding.cleanup().run();
        }
    }

    @Test
    void executeStageSql_runsAgainstLivePostgresWithoutSyntaxError() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(postgresBinding.dataSource());

        UUID projectId = UUID.fromString("27027d52-6862-443f-bad3-b33c1de2f31a");
        UUID snapshotId = UUID.fromString("2070abbf-db48-474f-a467-190863710215");

        String sql =
                """
                SELECT id, content, CAST(metadata AS TEXT) AS metadata_json, chunk_index,
                  ts_rank_cd(content_tsv, websearch_to_tsquery('simple', :query)) AS rank
                FROM vector_store
                WHERE project_id IS NOT DISTINCT FROM :projectId
                  AND metadata->>'indexSnapshotId' IS NOT NULL
                  AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                  AND content_tsv @@ websearch_to_tsquery('simple', :query)
                ORDER BY rank DESC NULLS LAST LIMIT :limit
                """;

        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotIds", List.of(snapshotId.toString()))
                        .addValue("query", "Resume los temas tratados en el acta del 25/02/2026")
                        .addValue("limit", 5);

        assertThatCode(() -> jdbc.query(sql, p, (rs, rowNum) -> rs.getString("id"))).doesNotThrowAnyException();
    }
}
