package com.uniovi.rag.application.service.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.Application;
import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.configuration.RagAccountProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntityFactory;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
class AccountLifecycleIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private AccountDeletionOrchestrator accountDeletionOrchestrator;

    @Autowired
    private AccountExportApplicationService accountExportApplicationService;

    @Autowired
    private AsyncTaskRepository asyncTaskRepository;

    @Autowired
    private AsyncTaskMutationService asyncTaskMutationService;

    @Autowired
    private BinaryStoragePort binaryStoragePort;

    @Value("${rag.storage.root}")
    private String binaryStorageRoot;

    @Autowired
    private RagAccountProperties accountProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void exportZip_containsManifestV2AndMessages(@TempDir Path exportRoot) throws Exception {
        accountProperties.setExportStorageDir(exportRoot.toString());
        Fixture fx = seedFixture();

        AsyncTaskEntity task =
                asyncTaskRepository.save(
                        AsyncTaskEntity.queued(fx.user(), AsyncTaskType.ACCOUNT_EXPORT, Map.of(), Instant.now()));
        accountExportApplicationService.runExport(task, asyncTaskMutationService);

        Path zipPath;
        try (var walk = Files.list(exportRoot.resolve(fx.user().getId().toString()))) {
            zipPath = walk.filter(p -> p.toString().endsWith(".zip")).findFirst().orElseThrow();
        }

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            JsonNode manifest =
                    objectMapper.readTree(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes());
            assertThat(manifest.get("schemaVersion").asInt())
                    .isEqualTo(AccountExportApplicationService.MANIFEST_SCHEMA_VERSION);
            assertThat(manifest.get("entries")).isNotEmpty();
            assertThat(zip.getEntry("messages.json")).isNotNull();
            assertThat(zip.getEntry("exclusions.json")).isNotNull();
            JsonNode messages =
                    objectMapper.readTree(zip.getInputStream(zip.getEntry("messages.json")).readAllBytes());
            assertThat(messages.get("items")).isNotEmpty();
        }
    }

    @Test
    void deleteUser_removesOwnedData_butPreservesOtherUserVectors(@TempDir Path exportRoot) throws Exception {
        accountProperties.setExportStorageDir(exportRoot.toString());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Fixture fx =
                tx.execute(
                        status -> {
                            try {
                                return seedFixture();
                            } catch (Exception e) {
                                throw new IllegalStateException("Failed to seed M7 delete fixture", e);
                            }
                        });
        OtherUserVector other =
                tx.execute(
                        status -> {
                            try {
                                return seedOtherUserVector();
                            } catch (Exception e) {
                                throw new IllegalStateException("Failed to seed other-user vector fixture", e);
                            }
                        });
        UUID deletedUserId = fx.user().getId();

        TransactionTemplate deleteTx = new TransactionTemplate(transactionManager);
        deleteTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        deleteTx.executeWithoutResult(
                status -> accountDeletionOrchestrator.deleteUserAccount(deletedUserId));

        assertThat(userRepository.findById(deletedUserId)).isEmpty();

        Long messages =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM messages m
                          JOIN conversations c ON c.id = m.conversation_id
                         WHERE c.user_id = ?
                        """,
                        Long.class,
                        deletedUserId);
        assertThat(messages).isZero();

        Long documents =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM project_documents pd
                          JOIN projects p ON p.id = pd.project_id
                         WHERE p.owner_id = ?
                        """,
                        Long.class,
                        deletedUserId);
        assertThat(documents).isZero();

        Long mailRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM mail_outbox WHERE lower(recipient) = lower(?)",
                        Long.class,
                        fx.email());
        assertThat(mailRows).isZero();

        Long ownedVectors =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM vector_store
                        WHERE metadata->>'projectDocumentId' = ?
                        """,
                        Long.class,
                        fx.documentId().toString());
        assertThat(ownedVectors).isZero();

        Long otherVectors =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM vector_store
                        WHERE metadata->>'projectDocumentId' = ?
                        """,
                        Long.class,
                        other.documentId().toString());
        assertThat(otherVectors).isEqualTo(1L);

        assertThat(Files.exists(fx.storedBinaryPath())).isFalse();

        Long campaigns =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation_campaign WHERE user_id = ?",
                        Long.class,
                        deletedUserId);
        assertThat(campaigns).isZero();

        Long runs =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation_run WHERE user_id = ?",
                        Long.class,
                        deletedUserId);
        assertThat(runs).isZero();

        Long confirmationTokens =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM email_confirmation_tokens WHERE user_id = ?",
                        Long.class,
                        deletedUserId);
        assertThat(confirmationTokens).isZero();
    }

    private Fixture seedFixture() throws Exception {
        Instant now = Instant.parse("2026-06-03T10:00:00Z");
        String email = "m7-it-" + UUID.randomUUID() + "@test.local";
        UserEntity user =
                userRepository.save(UserEntityFactory.newRegisteredUser(email, "M7 IT", "ph"));
        userRepository.flush();

        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO projects (id, owner_id, name, description, created_at, updated_at)
                VALUES (?, ?, 'm7-project', 'desc', ?, ?)
                """,
                projectId,
                user.getId(),
                Timestamp.from(now),
                Timestamp.from(now));
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow();

        ConversationEntity conversation =
                conversationRepository.save(ConversationEntity.create(user, project, "m7-chat", List.of()));
        messageRepository.save(MessageEntity.userMessage(conversation, "hello m7", 1));

        BinaryStoragePort.StoredObject stored =
                binaryStoragePort.store(
                        new ByteArrayInputStream("binary".getBytes(StandardCharsets.UTF_8)),
                        6,
                        project.getId() + "/" + UUID.randomUUID() + "/source.bin");
        Path binaryPath = Path.of(binaryStorageRoot).resolve(stored.relativeUri());

        KnowledgeDocumentEntity doc =
                knowledgeDocumentRepository.save(KnowledgeDocumentEntityFactory.newIngesting(project, "notes.pdf"));
        doc.setStatus(ProjectDocumentStatus.READY);
        doc.setStorageUri(stored.relativeUri());
        doc.setUploadedAt(now);
        doc = knowledgeDocumentRepository.save(doc);

        jdbcTemplate.update(
                """
                INSERT INTO vector_store (content, metadata, project_id)
                VALUES (?, ?::jsonb, ?)
                """,
                "personal chunk",
                "{\"projectDocumentId\": \"" + doc.getId() + "\"}",
                project.getId());

        UUID datasetId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO evaluation_dataset (id, owner_id, name, type, uploaded_at)
                VALUES (?, ?, 'm7-ds', 'RAG', ?)
                """,
                datasetId,
                user.getId(),
                Timestamp.from(now));
        jdbcTemplate.update(
                """
                INSERT INTO evaluation_run (id, user_id, dataset_id, type, config_ids, status, progress, created_at)
                VALUES (?, ?, ?, 'RAG_FULL', '[]'::jsonb, 'DONE', 100, ?)
                """,
                UUID.randomUUID(),
                user.getId(),
                datasetId,
                Timestamp.from(now));

        jdbcTemplate.update(
                """
                INSERT INTO evaluation_campaign (id, user_id, project_id, study_type, name, created_at)
                VALUES (?, ?, ?, 'M7_IT', 'camp', ?)
                """,
                UUID.randomUUID(),
                user.getId(),
                projectId,
                Timestamp.from(now));

        MailOutboxEntity mail = new MailOutboxEntity();
        mail.setPurpose("CONFIRM_EMAIL");
        mail.setRecipient(email);
        mail.setSubject("confirm");
        mail.setBodyText("token body");
        mail.setCreatedAt(now);
        mailOutboxRepository.save(mail);

        jdbcTemplate.update(
                """
                INSERT INTO email_confirmation_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """,
                user.getId(),
                "hash-" + UUID.randomUUID(),
                Timestamp.from(now.plusSeconds(3600)));

        return new Fixture(user, email, doc.getId(), binaryPath);
    }

    private OtherUserVector seedOtherUserVector() {
        Instant now = Instant.parse("2026-06-03T10:00:00Z");
        String email = "m7-other-" + UUID.randomUUID() + "@test.local";
        UserEntity otherUser =
                userRepository.save(UserEntityFactory.newRegisteredUser(email, "Other", "ph"));
        userRepository.flush();
        UUID otherProjectId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO projects (id, owner_id, name, description, created_at, updated_at)
                VALUES (?, ?, 'other-project', 'desc', ?, ?)
                """,
                otherProjectId,
                otherUser.getId(),
                Timestamp.from(now),
                Timestamp.from(now));
        ProjectEntity otherProject = projectRepository.findById(otherProjectId).orElseThrow();

        KnowledgeDocumentEntity otherDoc =
                knowledgeDocumentRepository.save(
                        KnowledgeDocumentEntityFactory.newIngesting(otherProject, "other.pdf"));
        otherDoc.setStatus(ProjectDocumentStatus.READY);
        otherDoc.setUploadedAt(now);
        otherDoc = knowledgeDocumentRepository.save(otherDoc);

        jdbcTemplate.update(
                """
                INSERT INTO vector_store (content, metadata, project_id)
                VALUES (?, ?::jsonb, ?)
                """,
                "other user chunk",
                "{\"projectDocumentId\": \"" + otherDoc.getId() + "\"}",
                otherProject.getId());

        return new OtherUserVector(otherDoc.getId());
    }

    private record Fixture(UserEntity user, String email, UUID documentId, Path storedBinaryPath) {}

    private record OtherUserVector(UUID documentId) {}

}
