package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.application.evaluation.EvaluationCustomConfigMapper;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.infrastructure.persistence.impl.MinuteDocumentRepositoryImpl;
import com.uniovi.rag.interfaces.rest.legacy.RagController;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import com.uniovi.rag.service.document.SimpleDocumentService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * JDBC integration tests: Testcontainers locally; in CI use the Postgres service (see {@code .github/workflows/ci.yml}
 * and {@code sonar.yml}) via {@code INTEGRATION_JDBC_URL} or automatic URL when {@code GITHUB_ACTIONS=true}.
 */
class DocumentPersistenceJdbcIntegrationTest {

    private static final String CI_DEFAULT_INTEGRATION_JDBC_URL = "jdbc:postgresql://localhost:5432/testdb";

    private static PostgreSQLContainer<?> postgresContainer;

    private static DataSource sharedDataSource;

    private JdbcTemplate jdbcTemplate;

    private SimpleDocumentService documentService;

    private MinuteDocumentRepositoryImpl repository;

    private static final String ZERO_VECTOR_1024 = buildZeroVector(1024);

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
            postgresContainer = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("test-init.sql");
            postgresContainer.start();
            sharedDataSource = new DriverManagerDataSource(
                    postgresContainer.getJdbcUrl(),
                    postgresContainer.getUsername(),
                    postgresContainer.getPassword()
            );
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

    /**
     * Prefer explicit {@code INTEGRATION_JDBC_URL}; on GitHub Actions fall back to the workflow Postgres service
     * so tests do not rely on Testcontainers when the job env is missing from the forked JVM.
     */
    private static String resolveExternalJdbcUrl() {
        String explicit = System.getenv("INTEGRATION_JDBC_URL");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            return CI_DEFAULT_INTEGRATION_JDBC_URL;
        }
        return null;
    }

    @BeforeEach
    void setUp() {
        DataSource ds = sharedDataSource;
        jdbcTemplate = new JdbcTemplate(ds);
        // Shared JDBC URL (e.g. CI Postgres) may hold rows from other test classes or earlier methods.
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM documents");

        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);

        // SimpleDocumentService uses vectorStore/chatClient for 'add' operations; these tests only use JDBC-backed methods.
        documentService = new SimpleDocumentService(vectorStore, chatClient, jdbcTemplate, 400);

        MetadataMinuteDocumentService metadataMinuteDocumentService = mock(MetadataMinuteDocumentService.class);
        repository = new MinuteDocumentRepositoryImpl(documentService, metadataMinuteDocumentService);
    }

    @Test
    void hasDocumentWithId_returnsTrue_whenMetadataHasDocumentId() {
        String docId = "doc-1";
        insertDocumentAndVectorStore(docId, Map.of("document_id", docId));

        assertTrue(repository.hasDocumentWithId(docId));
    }

    @Test
    void deleteById_deletesVectorStoreAndDocuments_whenMetadataHasDocumentId() {
        String docId = "doc-2";
        insertDocument(docId);
        insertVectorStoreChunk(Map.of("document_id", docId), 0);
        insertVectorStoreChunk(Map.of("document_id", docId), 1);

        int deleted = repository.deleteById(docId);
        assertEquals(2, deleted);

        int remainingVectorStore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
        int remainingDocs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Integer.class);
        assertEquals(0, remainingVectorStore);
        assertEquals(0, remainingDocs);
    }

    @Test
    void ragController_deleteDocumentById_noContent_andActuallyDeletesFromDb() {
        String docId = "doc-5";
        insertDocument(docId);
        insertVectorStoreChunk(Map.of("document_id", docId), 0);

        QueryService queryService = mock(QueryService.class);
        EvaluationService evaluationService = mock(EvaluationService.class);

        RagController controller = new RagController(
                documentService,
                queryService,
                evaluationService,
                repository,
                new EvaluationCustomConfigMapper(new RagImplementationProperties()),
                null
        );

        var response = controller.deleteDocumentById(docId);
        assertEquals(204, response.getStatusCode().value());

        int remainingVectorStore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
        int remainingDocs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Integer.class);
        assertEquals(0, remainingVectorStore);
        assertEquals(0, remainingDocs);
    }

    @Test
    void hasDocumentWithId_usesFallbackToMetadataId_whenDocumentIdMissing() {
        String docId = "doc-3";
        insertDocumentAndVectorStore(docId, Map.of("id", docId));

        assertTrue(repository.hasDocumentWithId(docId));
    }

    @Test
    void deleteById_deletesVectorStoreChunks_usingMetadataIdFallback_whenDocumentIdMissing() {
        String docId = "doc-4";
        insertDocument(docId);
        insertVectorStoreChunk(Map.of("id", docId), 0);

        int deleted = repository.deleteById(docId);
        assertEquals(1, deleted);

        int remainingVectorStore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
        assertEquals(0, remainingVectorStore);
    }

    private void insertDocumentAndVectorStore(String docId, Map<String, String> metadata) {
        insertDocument(docId);
        insertVectorStoreChunk(metadata, 0);
    }

    private void insertDocument(String docId) {
        jdbcTemplate.update(
                "INSERT INTO documents (document_name, metadata) VALUES (?, ?::jsonb)",
                docId,
                "{\"filename\":\"test\"}"
        );
    }

    private void insertVectorStoreChunk(Map<String, String> metadata, int chunkIndex) {
        // metadata JSON must include document_id or id depending on the test case.
        String metadataJson = toJson(metadata);
        jdbcTemplate.update(
                "INSERT INTO vector_store (content, metadata, embedding, chunk_index) VALUES (?, ?::jsonb, ?::vector, ?)",
                "content",
                metadataJson,
                ZERO_VECTOR_1024,
                chunkIndex
        );
    }

    private static String buildZeroVector(int dims) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < dims; i++) {
            if (i > 0) sb.append(',');
            sb.append('0');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String toJson(Map<String, String> map) {
        // Minimal JSON for tests (no extra libraries).
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('\"').append(escape(e.getKey())).append('\"').append(':')
                    .append('\"').append(escape(e.getValue())).append('\"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

