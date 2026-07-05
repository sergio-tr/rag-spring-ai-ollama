package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.impl.MinuteDocumentRepositoryImpl;
import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;
import com.uniovi.rag.application.service.knowledge.document.SimpleDocumentService;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport.PostgresBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * JDBC integration tests: Testcontainers locally; in CI use the Postgres service (see {@code .github/workflows/ci.yml}
 * and {@code sonar.yml}) via {@code INTEGRATION_JDBC_URL} or automatic URL when {@code GITHUB_ACTIONS=true}.
 */
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isJdbcIntegrationTestAvailable",
        disabledReason = "Start Postgres (e.g. .github/local/ci-like-verify.sh) or set INTEGRATION_JDBC_URL")
class DocumentPersistenceJdbcIntegrationTest {

    private static PostgresBinding postgresBinding;

    private JdbcTemplate jdbcTemplate;

    private SimpleDocumentService documentService;

    private MinuteDocumentRepositoryImpl repository;

    private static final String ZERO_VECTOR_1024 = buildZeroVector(1024);

    @BeforeAll
    static void startOrBindDatabase() {
        postgresBinding = PostgresIntegrationTestSupport.startJdbcIntegrationDatabase();
    }

    @AfterAll
    static void stopContainer() {
        if (postgresBinding != null) {
            postgresBinding.cleanup().run();
        }
    }

    @BeforeEach
    void setUp() {
        DataSource ds = postgresBinding.dataSource();
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
