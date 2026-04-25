package com.uniovi.rag.infrastructure.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.Application;
import com.uniovi.rag.application.service.MoveConversationApplicationService;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.support.ResolvedConfigSnapshotTestFixtures;
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
class MoveConversationApplicationServiceIT {

    @Autowired
    private MoveConversationApplicationService moveConversationApplicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    @Autowired
    private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void moveConversationToProject_clearsFilter_movesChatLocalDocs_alignsConversationSnapshot() {
        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        UserEntity user =
                userRepository.save(
                        UserEntityFactory.newRegisteredUser(
                                "move-conv-" + UUID.randomUUID() + "@test.local", "Move User", "ph"));

        ProjectEntity projectA = new ProjectEntity();
        projectA.setOwner(user);
        projectA.setName("Project A");
        projectA.setCreatedAt(now);
        projectA.setUpdatedAt(now);
        projectA = projectRepository.save(projectA);

        ProjectEntity projectB = new ProjectEntity();
        projectB.setOwner(user);
        projectB.setName("Project B");
        projectB.setCreatedAt(now);
        projectB.setUpdatedAt(now);
        projectB = projectRepository.save(projectB);

        ConversationEntity conv =
                conversationRepository.save(ConversationEntity.create(user, projectA, "Chat", List.of()));
        conv.setDocumentFilter(List.of(UUID.randomUUID().toString()));
        conv = conversationRepository.save(conv);

        KnowledgeDocumentEntity chatDoc =
                knowledgeDocumentRepository.save(
                        KnowledgeDocumentEntityFactory.newChatLocalIngesting(projectA, conv, "chat-local.txt"));
        KnowledgeDocumentEntity sharedDoc =
                knowledgeDocumentRepository.save(KnowledgeDocumentEntityFactory.newIngesting(projectA, "shared.pdf"));

        UUID snapCfgId = UUID.randomUUID();
        var resSnap =
                ResolvedConfigSnapshotTestFixtures.newEntityForPersistence(
                        objectMapper, snapCfgId, user.getId(), projectA.getId(), "move-hash");
        resSnap = resolvedConfigSnapshotRepository.save(resSnap);

        KnowledgeIndexSnapshotEntity index = new KnowledgeIndexSnapshotEntity();
        index.setSignatureHash("move-sig");
        index.setScopeType(KnowledgeSnapshotScopeType.CONVERSATION);
        index.setProject(projectA);
        index.setConversation(conv);
        index.setStatus(IndexSnapshotStatus.ACTIVE);
        index.setResolvedConfigSnapshotId(resSnap.getId());
        index.setResolvedConfigHash("rh-move");
        index.setCreatedAt(now);
        index.setUpdatedAt(now);
        knowledgeIndexSnapshotRepository.save(index);

        knowledgeDocumentRepository.flush();
        entityManager.clear();

        moveConversationApplicationService.moveConversationToProject(
                user.getId(), projectA.getId(), conv.getId(), projectB.getId());

        entityManager.clear();

        ConversationEntity loadedConv = conversationRepository.findById(conv.getId()).orElseThrow();
        assertThat(loadedConv.getProject().getId()).isEqualTo(projectB.getId());
        assertThat(loadedConv.getDocumentFilter()).isEmpty();

        KnowledgeDocumentEntity reloadedChat = knowledgeDocumentRepository.findById(chatDoc.getId()).orElseThrow();
        assertThat(reloadedChat.getProject().getId()).isEqualTo(projectB.getId());

        KnowledgeDocumentEntity reloadedShared =
                knowledgeDocumentRepository.findById(sharedDoc.getId()).orElseThrow();
        assertThat(reloadedShared.getProject().getId()).isEqualTo(projectA.getId());

        KnowledgeIndexSnapshotEntity snap =
                knowledgeIndexSnapshotRepository
                        .findActiveConversationSnapshot(
                                conv.getId(), KnowledgeSnapshotScopeType.CONVERSATION, IndexSnapshotStatus.ACTIVE)
                        .orElseThrow();
        assertThat(snap.getProject().getId()).isEqualTo(projectB.getId());
    }
}
