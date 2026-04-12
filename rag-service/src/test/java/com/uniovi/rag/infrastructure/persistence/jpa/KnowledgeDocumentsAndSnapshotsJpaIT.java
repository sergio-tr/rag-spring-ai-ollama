package com.uniovi.rag.infrastructure.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.Application;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.DocumentArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.ReindexEventRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.mapper.KnowledgeIndexSnapshotMapper;
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
class KnowledgeDocumentsAndSnapshotsJpaIT {

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
    private KnowledgeSnapshotDocumentRepository knowledgeSnapshotDocumentRepository;

    @Autowired
    private DocumentArtifactRepository documentArtifactRepository;

    @Autowired
    private ReindexEventRepository reindexEventRepository;

    @Autowired
    private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistKnowledgeDocumentIndexSnapshotLinkArtifactAndReindexEvent() {
        Instant now = Instant.parse("2026-05-20T09:00:00Z");
        UserEntity user =
                userRepository.save(
                        UserEntityFactory.newRegisteredUser(
                                "jpa-kn-" + UUID.randomUUID() + "@test.local", "Kn User", "ph"));

        ProjectEntity project = new ProjectEntity();
        project.setOwner(user);
        project.setName("kn-project");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        var conv =
                conversationRepository.save(
                        ConversationEntity.create(user, project, "kn-conv", java.util.List.of()));

        UUID snapCfgId = UUID.randomUUID();
        ResolvedConfigSnapshotEntity resSnap =
                ResolvedConfigSnapshotTestFixtures.newEntityForPersistence(
                        objectMapper, snapCfgId, user.getId(), project.getId(), "kn-snap");
        resSnap = resolvedConfigSnapshotRepository.save(resSnap);

        KnowledgeDocumentEntity doc =
                knowledgeDocumentRepository.save(
                        KnowledgeDocumentEntityFactory.newChatLocalIngesting(project, conv, "notes.pdf"));

        KnowledgeIndexSnapshotEntity index = new KnowledgeIndexSnapshotEntity();
        index.setSignatureHash("sig-1");
        index.setScopeType(KnowledgeSnapshotScopeType.CONVERSATION);
        index.setProject(project);
        index.setConversation(conv);
        index.setStatus(IndexSnapshotStatus.ACTIVE);
        index.setResolvedConfigSnapshotId(resSnap.getId());
        index.setResolvedConfigHash("rh-1");
        index.setCreatedAt(now);
        index.setUpdatedAt(now);
        index = knowledgeIndexSnapshotRepository.save(index);

        KnowledgeSnapshotDocumentEntity link = new KnowledgeSnapshotDocumentEntity();
        link.setId(new KnowledgeSnapshotDocumentPk(index.getId(), doc.getId()));
        link.setSnapshot(index);
        link.setDocument(doc);
        knowledgeSnapshotDocumentRepository.save(link);

        DocumentArtifactEntity art = DocumentArtifactEntity.newRow();
        art.setDocument(doc);
        art.setArtifactType(DocumentArtifactType.METADATA);
        art.setPayloadJsonb(Map.of("pages", 3));
        art.setContentHash("abc");
        art.setCreatedAt(now);
        documentArtifactRepository.save(art);

        ReindexEventEntity evt =
                ReindexEventEntity.newPending(
                        project,
                        conv,
                        doc,
                        "INGEST",
                        "sig-1",
                        ReindexEventStatus.PENDING,
                        resSnap.getId());
        reindexEventRepository.save(evt);

        knowledgeDocumentRepository.flush();
        entityManager.clear();

        KnowledgeIndexSnapshotEntity loadedIdx =
                knowledgeIndexSnapshotRepository.findById(index.getId()).orElseThrow();
        assertThat(loadedIdx.getSignatureHash()).isEqualTo("sig-1");
        assertThat(KnowledgeIndexSnapshotMapper.toDomain(loadedIdx).resolvedConfigSnapshotId())
                .isEqualTo(resSnap.getId());

        KnowledgeSnapshotDocumentEntity loadedLink =
                knowledgeSnapshotDocumentRepository
                        .findById(new KnowledgeSnapshotDocumentPk(index.getId(), doc.getId()))
                        .orElseThrow();
        assertThat(loadedLink.getDocument().getId()).isEqualTo(doc.getId());
    }
}
