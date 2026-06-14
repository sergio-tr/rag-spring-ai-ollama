package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.application.service.runtime.retrieval.SparseDomainSynonyms;
import com.uniovi.rag.application.service.runtime.retrieval.SparseQueryPreparer;
import com.uniovi.rag.application.service.runtime.retrieval.SparseRetrievalStrategy;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isJdbcIntegrationTestAvailable",
        disabledReason = "Start Postgres or set INTEGRATION_JDBC_URL")
class SparseRetrievalStrategyIntegrationIT {

    private static final String PROBE_TOKEN = "zxqv_sparse_probe_token";
    private static final String ZERO_VECTOR_1024 = buildZeroVector(1024);

    private static PostgreSQLContainer<?> postgresContainer;
    private static DataSource sharedDataSource;

    private JdbcTemplate jdbcTemplate;
    private SparseRetrievalStrategy sparseRetrievalStrategy;
    private Boolean vectorStoreProjectFkEnforced;

    @BeforeAll
    static void startOrBindDatabase() {
        String externalUrl = resolveExternalJdbcUrl();
        if (externalUrl != null && !externalUrl.isBlank()) {
            String user = Optional.ofNullable(System.getenv("SPRING_DATASOURCE_USERNAME")).orElse("postgres");
            String password = Optional.ofNullable(System.getenv("SPRING_DATASOURCE_PASSWORD")).orElse("postgres");
            sharedDataSource = new DriverManagerDataSource(externalUrl, user, password);
            return;
        }
        try {
            postgresContainer =
                    new PostgreSQLContainer<>("pgvector/pgvector:0.8.2-pg16-bookworm")
                            .withDatabaseName("testdb")
                            .withUsername("test")
                            .withPassword("test")
                            .withInitScript("test-init.sql");
            postgresContainer.start();
            sharedDataSource =
                    new DriverManagerDataSource(
                            postgresContainer.getJdbcUrl(),
                            postgresContainer.getUsername(),
                            postgresContainer.getPassword());
        } catch (Throwable t) {
            Assumptions.abort("Postgres via Testcontainers unavailable: " + t.getMessage());
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        SparseQueryPreparer preparer = new SparseQueryPreparer(new SparseDomainSynonyms());
        sparseRetrievalStrategy =
                new SparseRetrievalStrategy(
                        new NamedParameterJdbcTemplate(sharedDataSource), preparer, new SparseDomainSynonyms());
        vectorStoreProjectFkEnforced = null;
        jdbcTemplate.update("DELETE FROM vector_store");
    }

    @Test
    void retrieve_spanishDomainTerm_returnsSparseCandidate() {
        UUID snapshotId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        insertChunk(snapshotId, projectId, documentId, "Se acordó reparar el ascensor del edificio.", 0);

        RetrievalRequest req =
                request(
                        snapshotId,
                        projectId,
                        "¿Cuántas actas mencionan el ascensor?",
                        List.of("all"),
                        true);

        List<RetrievalCandidate> hits = sparseRetrievalStrategy.retrieve(req);

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().content()).containsIgnoringCase("ascensor");
    }

    @Test
    void retrieve_positiveHit_returnsSparseCandidateWithOriginAndMetadata() {
        UUID snapshotId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        insertChunk(snapshotId, projectId, documentId, PROBE_TOKEN + " alpha beta", 0);

        RetrievalRequest req = request(snapshotId, projectId, PROBE_TOKEN, List.of("all"), true);

        List<RetrievalCandidate> hits = sparseRetrievalStrategy.retrieve(req);

        assertThat(hits).isNotEmpty();
        RetrievalCandidate first = hits.getFirst();
        assertThat(first.metadata().get("retrievalOrigin")).isEqualTo("SPARSE");
        assertThat(first.metadata().get("indexSnapshotId")).isEqualTo(snapshotId.toString());
        assertThat(first.metadata().get("document_id")).isEqualTo(documentId.toString());
        assertThat(first.content()).contains(PROBE_TOKEN);
    }

    @Test
    void retrieve_documentAllowlist_excludesChunkOutsideScope() {
        UUID snapshotId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID allowedDoc = UUID.randomUUID();
        UUID otherDoc = UUID.randomUUID();
        insertChunk(snapshotId, projectId, allowedDoc, PROBE_TOKEN + " allowed", 0);
        insertChunk(snapshotId, projectId, otherDoc, PROBE_TOKEN + " blocked", 1);

        List<RetrievalCandidate> scoped =
                sparseRetrievalStrategy.retrieve(
                        request(snapshotId, projectId, PROBE_TOKEN, List.of(allowedDoc.toString()), false));

        assertThat(scoped).hasSize(1);
        assertThat(scoped.getFirst().metadata().get("document_id")).isEqualTo(allowedDoc.toString());
    }

    @Test
    void retrieve_unrelatedQuery_returnsEmptyWithoutError() {
        UUID snapshotId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        insertChunk(snapshotId, projectId, documentId, PROBE_TOKEN, 0);

        List<RetrievalCandidate> hits =
                sparseRetrievalStrategy.retrieve(request(snapshotId, projectId, "totally_unrelated_terms", List.of("all"), true));

        assertThat(hits).isEmpty();
    }

    private void insertChunk(
            UUID snapshotId, UUID projectId, UUID documentId, String content, int chunkIndex) {
        ensureProjectExists(projectId);
        String metadata =
                "{\"indexSnapshotId\":\""
                        + snapshotId
                        + "\",\"document_id\":\"906390506\",\"documentId\":\""
                        + documentId
                        + "\",\"projectDocumentId\":\""
                        + documentId
                        + "\",\"chunk_index\":"
                        + chunkIndex
                        + "}";
        jdbcTemplate.update(
                "INSERT INTO vector_store (content, metadata, embedding, chunk_index, project_id) VALUES (?, ?::jsonb, ?::vector, ?, ?)",
                content,
                metadata,
                ZERO_VECTOR_1024,
                chunkIndex,
                projectId);
    }

    private static RetrievalRequest request(
            UUID snapshotId, UUID projectId, String query, List<String> allowlist, boolean allDocs) {
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
                List.of(snapshotId),
                projectId,
                Optional.empty(),
                allowlist,
                allDocs,
                Optional.empty());
    }

    private void ensureProjectExists(UUID projectId) {
        if (!isVectorStoreProjectFkEnforced()) {
            return;
        }
        Integer existing =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM projects WHERE id = ?", Integer.class, projectId);
        if (existing != null && existing > 0) {
            return;
        }
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                userId,
                "sparse_it+" + projectId + "@test.local",
                "{noop}test",
                "USER");
        jdbcTemplate.update(
                "INSERT INTO projects (id, owner_id, name, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
                projectId,
                userId,
                "SPARSE_IT_" + projectId);
    }

    private boolean isVectorStoreProjectFkEnforced() {
        if (vectorStoreProjectFkEnforced == null) {
            Integer count =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)::int FROM information_schema.table_constraints
                            WHERE table_name = 'vector_store'
                              AND constraint_name = 'vector_store_project_id_fkey'
                            """,
                            Integer.class);
            vectorStoreProjectFkEnforced = count != null && count > 0;
        }
        return vectorStoreProjectFkEnforced;
    }

    private static String resolveExternalJdbcUrl() {
        String explicit = System.getenv("INTEGRATION_JDBC_URL");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            return "jdbc:postgresql://localhost:5432/testdb";
        }
        return null;
    }

    private static String buildZeroVector(int dims) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < dims; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('0');
        }
        sb.append(']');
        return sb.toString();
    }
}
