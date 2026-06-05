package com.uniovi.rag.application.service.account;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.configuration.RagAccountProperties;
import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ordered purge of user-owned data before {@code users} row deletion (M7 account lifecycle).
 */
@Component
public class AccountDeletionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionOrchestrator.class);

    private static final String DEFAULT_CLASSIFIER_TAG = "default";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final ClassifierModelRepository classifierModelRepository;
    private final MailOutboxRepository mailOutboxRepository;
    private final RuntimeTraceRegressionSuiteDefinitionRepository regressionDefinitionRepository;
    private final RuntimeTraceRegressionSuiteRunRepository regressionRunRepository;
    private final VectorStoreOwnerPurge vectorStoreOwnerPurge;
    private final BinaryStoragePort binaryStoragePort;
    private final EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final RagAccountProperties accountProperties;
    private final EntityManager entityManager;

    public AccountDeletionOrchestrator(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            EvaluationDatasetRepository evaluationDatasetRepository,
            ClassifierModelRepository classifierModelRepository,
            MailOutboxRepository mailOutboxRepository,
            RuntimeTraceRegressionSuiteDefinitionRepository regressionDefinitionRepository,
            RuntimeTraceRegressionSuiteRunRepository regressionRunRepository,
            VectorStoreOwnerPurge vectorStoreOwnerPurge,
            BinaryStoragePort binaryStoragePort,
            EvaluationDatasetStorePort evaluationDatasetStorePort,
            RagAccountProperties accountProperties,
            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.classifierModelRepository = classifierModelRepository;
        this.mailOutboxRepository = mailOutboxRepository;
        this.regressionDefinitionRepository = regressionDefinitionRepository;
        this.regressionRunRepository = regressionRunRepository;
        this.vectorStoreOwnerPurge = vectorStoreOwnerPurge;
        this.binaryStoragePort = binaryStoragePort;
        this.evaluationDatasetStorePort = evaluationDatasetStorePort;
        this.accountProperties = accountProperties;
        this.entityManager = entityManager;
    }

    @Transactional
    public void deleteUserAccount(UUID userId) {
        UserEntity user =
                userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("User not found"));
        String email = user.getEmail();

        List<ProjectEntity> projects =
                projectRepository.findByOwner_IdOrderByUpdatedAtDesc(userId, Pageable.unpaged()).getContent();
        List<UUID> projectIds = projects.stream().map(ProjectEntity::getId).toList();

        List<KnowledgeDocumentEntity> documents = knowledgeDocumentRepository.findAllByProjectOwner_Id(userId);
        List<UUID> documentIds = documents.stream().map(KnowledgeDocumentEntity::getId).toList();

        vectorStoreOwnerPurge.purgeForDocumentIds(documentIds);
        vectorStoreOwnerPurge.purgeForProjectIds(projectIds);

        purgeFilesystemArtifacts(userId, documents);

        mailOutboxRepository.deleteByRecipientIgnoreCase(email);

        regressionRunRepository.findAllByUserIdOrderByCreatedAtDescIdAsc(userId).forEach(regressionRunRepository::delete);
        regressionDefinitionRepository
                .findAllByUserIdOrderByUpdatedAtDescNameAscIdAsc(userId)
                .forEach(regressionDefinitionRepository::delete);

        // Drop loaded ownership graph so DB CASCADE on users does not fight stale associations.
        entityManager.flush();
        entityManager.clear();

        userRepository.deleteById(userId);
    }

    private void purgeFilesystemArtifacts(UUID userId, List<KnowledgeDocumentEntity> documents) {
        for (KnowledgeDocumentEntity doc : documents) {
            deleteStorageUriQuietly(doc.getStorageUri());
        }

        for (EvaluationDatasetEntity ds : evaluationDatasetRepository.findByOwner_IdOrderByUploadedAtDesc(userId)) {
            if (ds.getOwner() == null) {
                continue;
            }
            deleteEvaluationDatasetQuietly(ds.getStorageUri());
        }

        for (ClassifierModelEntity model : classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)) {
            deleteClassifierArtifactQuietly(model.getArtifactPath());
        }

        deleteExportDirectoryQuietly(userId);
    }

    private void deleteStorageUriQuietly(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return;
        }
        try {
            binaryStoragePort.delete(storageUri);
        } catch (IOException e) {
            log.warn("Could not delete document binary {}: {}", storageUri, e.getMessage());
        }
    }

    private void deleteEvaluationDatasetQuietly(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return;
        }
        try {
            evaluationDatasetStorePort.delete(storageUri);
        } catch (IOException e) {
            log.warn("Could not delete evaluation dataset file {}: {}", storageUri, e.getMessage());
        }
    }

    private void deleteClassifierArtifactQuietly(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            return;
        }
        String normalized = artifactPath.toLowerCase(Locale.ROOT);
        if (normalized.contains(DEFAULT_CLASSIFIER_TAG)) {
            return;
        }
        try {
            Path path = Path.of(artifactPath);
            if (path.isAbsolute()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete classifier artifact {}: {}", artifactPath, e.getMessage());
        }
    }

    private void deleteExportDirectoryQuietly(UUID userId) {
        Path userExportDir = accountProperties.getExportStorageDir().resolve(userId.toString());
        if (!Files.isDirectory(userExportDir)) {
            return;
        }
        try (var stream = Files.walk(userExportDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete export path {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk export directory {}: {}", userExportDir, e.getMessage());
        }
    }
}
