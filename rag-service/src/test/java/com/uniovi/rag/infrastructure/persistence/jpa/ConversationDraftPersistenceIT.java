package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.Application;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.ConversationDraftRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
class ConversationDraftPersistenceIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationDraftRepository conversationDraftRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void saveAndRoundTrip_persistsDraftRow() {
        Instant now = Instant.now();
        UserEntity user =
                new UserEntity() {
                    {
                        setEmail("it-draft-" + UUID.randomUUID() + "@test.local");
                        setPasswordHash("x");
                        setRole(UserRole.USER);
                        setCreatedAt(now);
                    }
                };
        user = userRepository.save(user);

        ProjectEntity project = new ProjectEntity();
        project.setOwner(user);
        project.setName("it-draft-project");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        ConversationEntity conv = ConversationEntity.create(user, project, "it-draft-conv", List.of());
        conv = conversationRepository.save(conv);

        ConversationDraftEntity draft = ConversationDraftEntity.create(conv, "draft body", now);
        draft = conversationDraftRepository.save(draft);
        assertThat(draft.getId()).isEqualTo(conv.getId());

        conversationDraftRepository.flush();
        entityManager.clear();

        ConversationDraftEntity loaded =
                conversationDraftRepository.findById(conv.getId()).orElseThrow();
        assertThat(loaded.getContent()).isEqualTo("draft body");
    }

    @Test
    @Transactional
    void updateContent_roundTripsUpdatedText() {
        Instant now = Instant.now();
        UserEntity user =
                new UserEntity() {
                    {
                        setEmail("it-draft-upd-" + UUID.randomUUID() + "@test.local");
                        setPasswordHash("x");
                        setRole(UserRole.USER);
                        setCreatedAt(now);
                    }
                };
        user = userRepository.save(user);

        ProjectEntity project = new ProjectEntity();
        project.setOwner(user);
        project.setName("it-draft-upd-project");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        ConversationEntity conv = ConversationEntity.create(user, project, "it-draft-upd-conv", List.of());
        conv = conversationRepository.save(conv);

        ConversationDraftEntity draft = ConversationDraftEntity.create(conv, "v1", now);
        conversationDraftRepository.save(draft);

        ConversationDraftEntity managed =
                conversationDraftRepository.findById(conv.getId()).orElseThrow();
        managed.setContent("v2");
        managed.setUpdatedAt(Instant.parse("2026-06-01T12:00:00Z"));
        conversationDraftRepository.save(managed);

        conversationDraftRepository.flush();
        entityManager.clear();

        ConversationDraftEntity loaded =
                conversationDraftRepository.findById(conv.getId()).orElseThrow();
        assertThat(loaded.getContent()).isEqualTo("v2");
        assertThat(loaded.getUpdatedAt()).isEqualTo(Instant.parse("2026-06-01T12:00:00Z"));
    }
}
