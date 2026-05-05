package com.uniovi.rag.infrastructure;

import com.uniovi.Application;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.service.retriever.NaiveCorpusContextService;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestEnvironment;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
                "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
                "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Postgres/Testcontainers not available")
class VectorStoreProjectIdBackfillIntegrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NaiveCorpusContextService naiveCorpusContextService;

    @AfterEach
    void clearContext() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void backfill_setsVectorStoreProjectIdColumn_fromMetadataProjectId() {
        UUID projectId = UUID.randomUUID();
        UUID projectDocumentId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO vector_store (content, metadata, embedding, chunk_index, project_id)
                VALUES (?, ?::jsonb, NULL, 0, NULL)
                """,
                "TFG_R0_BACKFILL_SMOKE",
                TestJson.json(Map.of(
                        "projectId", projectId.toString(),
                        "projectDocumentId", projectDocumentId.toString(),
                        "document_id", projectDocumentId.toString(),
                        "chunkIndex", 0,
                        "totalChunks", 1
                )));

        Integer before =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vector_store WHERE project_id IS NULL AND metadata->>'projectId' = ?",
                        Integer.class,
                        projectId.toString());
        assertThat(before).isNotNull();
        assertThat(before).isGreaterThanOrEqualTo(1);

        // Apply the same predicate as the Flyway migration (regex + project_id null).
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE vector_store
                        SET project_id = (metadata->>'projectId')::uuid
                        WHERE project_id IS NULL
                          AND metadata ? 'projectId'
                          AND (metadata->>'projectId') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                          AND metadata->>'projectId' = ?
                        """,
                        projectId.toString());
        assertThat(updated).isGreaterThanOrEqualTo(1);

        Integer after =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vector_store WHERE project_id = ?",
                        Integer.class,
                        projectId);
        assertThat(after).isNotNull();
        assertThat(after).isGreaterThanOrEqualTo(1);

        // Prove project-scoped retrieval paths that rely on the column can now see rows.
        RagConfig cfg =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false,
                        5,
                        0.7,
                        "l",
                        "e",
                        "c",
                        "simple",
                        true,
                        10_000,
                        10_000,
                        MaterializationStrategy.CHUNK_LEVEL);
        RagExecutionContextHolder.set(new RagExecutionContext(null, null, projectId.toString(), cfg, List.of("all"), "it"));

        String ctx = naiveCorpusContextService.buildNaiveCorpusContextIfConfigured();
        assertThat(ctx).contains("TFG_R0_BACKFILL_SMOKE");
    }

    /**
     * Minimal JSON helper to avoid extra deps in IT.
     */
    static final class TestJson {
        private TestJson() {}

        static String json(Map<String, Object> m) {
            // Very small and safe for our test map: keys/values are primitives and UUID strings.
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(e.getKey())).append("\":");
                Object v = e.getValue();
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof Number || v instanceof Boolean) {
                    sb.append(v);
                } else {
                    sb.append("\"").append(escape(String.valueOf(v))).append("\"");
                }
            }
            sb.append("}");
            return sb.toString();
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}

