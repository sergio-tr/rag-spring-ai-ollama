package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountExportSnapshotLoader {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UserPersonalizationRepository userPersonalizationRepository;
    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final MessageRepository messageRepository;
    private final RagConfigurationRepository ragConfigurationRepository;
    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final ClassifierModelRepository classifierModelRepository;

    public AccountExportSnapshotLoader(
            UserRepository userRepository,
            UserPreferencesRepository userPreferencesRepository,
            UserPersonalizationRepository userPersonalizationRepository,
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            MessageRepository messageRepository,
            RagConfigurationRepository ragConfigurationRepository,
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationRunRepository evaluationRunRepository,
            EvaluationCampaignRepository evaluationCampaignRepository,
            ClassifierModelRepository classifierModelRepository) {
        this.userRepository = userRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.userPersonalizationRepository = userPersonalizationRepository;
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.messageRepository = messageRepository;
        this.ragConfigurationRepository = ragConfigurationRepository;
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.classifierModelRepository = classifierModelRepository;
    }

    @Transactional(readOnly = true)
    public ExportSnapshot load(UUID userId) {
        UserEntity freshUser = userRepository.findById(userId).orElseThrow();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", freshUser.getEmail());
        profile.put("name", freshUser.getName());
        profile.put("emailVerified", freshUser.isEmailVerified());
        profile.put("emailVerifiedAt", freshUser.getEmailVerifiedAt() != null ? freshUser.getEmailVerifiedAt().toString() : null);
        profile.put("privacyAcceptedAt", freshUser.getPrivacyAcceptedAt() != null ? freshUser.getPrivacyAcceptedAt().toString() : null);
        profile.put("termsAcceptedAt", freshUser.getTermsAcceptedAt() != null ? freshUser.getTermsAcceptedAt().toString() : null);

        Map<String, Object> prefs =
                userPreferencesRepository
                        .findById(userId)
                        .map(
                                e ->
                                        e.getPreferences() != null
                                                ? new LinkedHashMap<>(e.getPreferences())
                                                : new LinkedHashMap<String, Object>())
                        .orElseGet(LinkedHashMap::new);

        Map<String, Object> pers =
                userPersonalizationRepository
                        .findById(userId)
                        .map(
                                e ->
                                        e.getPersonalization() != null
                                                ? new LinkedHashMap<>(e.getPersonalization())
                                                : new LinkedHashMap<String, Object>())
                        .orElseGet(LinkedHashMap::new);

        List<ProjectEntity> projects =
                projectRepository
                        .findByOwner_IdOrderByUpdatedAtDesc(userId, Pageable.unpaged())
                        .getContent();
        List<Map<String, Object>> projectMaps = new ArrayList<>();
        for (ProjectEntity p : projects) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId().toString());
            pm.put("name", p.getName());
            pm.put("description", p.getDescription());
            pm.put("projectPrompt", p.getProjectPrompt());
            pm.put("colorHex", p.getColorHex());
            pm.put("iconKey", p.getIconKey());
            projectMaps.add(pm);
        }

        List<Map<String, Object>> convMaps = new ArrayList<>();
        for (ProjectEntity p : projects) {
            for (ConversationEntity c : conversationRepository.findByProject_IdOrderByUpdatedAtDesc(p.getId())) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.getId().toString());
                cm.put("projectId", c.getProject().getId().toString());
                cm.put("title", c.getTitle());
                convMaps.add(cm);
            }
        }

        List<Map<String, Object>> messageMaps = new ArrayList<>();
        for (MessageEntity m : messageRepository.findAllByConversationUser_Id(userId)) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId().toString());
            mm.put("conversationId", m.getConversation().getId().toString());
            mm.put("role", m.getRole().name());
            mm.put("content", m.getContent());
            mm.put("seq", m.getSeq());
            mm.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            messageMaps.add(mm);
        }

        List<Map<String, Object>> docMaps = new ArrayList<>();
        for (KnowledgeDocumentEntity d : knowledgeDocumentRepository.findAllByProjectOwner_Id(userId)) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.getId().toString());
            dm.put("projectId", d.getProject().getId().toString());
            dm.put(
                    "conversationId",
                    d.getConversation() != null ? d.getConversation().getId().toString() : null);
            dm.put("corpusScope", d.getCorpusScope().name());
            dm.put("fileName", d.getFileName());
            dm.put("status", d.getStatus().name());
            dm.put("uploadedAt", d.getUploadedAt() != null ? d.getUploadedAt().toString() : null);
            dm.put("byteSize", d.getByteSize());
            dm.put("storageUri", d.getStorageUri());
            docMaps.add(dm);
        }

        List<Map<String, Object>> configMaps = new ArrayList<>();
        for (RagConfigurationEntity cfg : ragConfigurationRepository.findByUser_IdOrderByUpdatedAtDesc(userId)) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", cfg.getId().toString());
            cm.put("level", cfg.getLevel().name());
            cm.put("name", cfg.getName());
            cm.put("projectId", cfg.getProject() != null ? cfg.getProject().getId().toString() : null);
            cm.put("active", cfg.isActive());
            cm.put("values", cfg.getValues() != null ? cfg.getValues() : Map.of());
            configMaps.add(cm);
        }

        Map<String, Object> labSummary = new LinkedHashMap<>();
        List<Map<String, Object>> datasetSummaries = new ArrayList<>();
        for (EvaluationDatasetEntity ds : evaluationDatasetRepository.findByOwner_IdOrderByUploadedAtDesc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ds.getId().toString());
            row.put("name", ds.getName());
            row.put("type", ds.getType() != null ? ds.getType().name() : null);
            row.put("fileName", ds.getFileName());
            datasetSummaries.add(row);
        }
        labSummary.put("datasets", datasetSummaries);

        List<Map<String, Object>> runSummaries = new ArrayList<>();
        for (EvaluationRunEntity run : evaluationRunRepository.findByUser_IdOrderByCreatedAtDesc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", run.getId().toString());
            row.put("datasetId", run.getDataset() != null ? run.getDataset().getId().toString() : null);
            row.put("type", run.getType() != null ? run.getType().name() : null);
            row.put("status", run.getStatus() != null ? run.getStatus().name() : null);
            row.put("createdAt", run.getCreatedAt() != null ? run.getCreatedAt().toString() : null);
            runSummaries.add(row);
        }
        labSummary.put("runs", runSummaries);

        List<Map<String, Object>> campaignSummaries = new ArrayList<>();
        for (EvaluationCampaignEntity campaign : evaluationCampaignRepository.findByUser_IdOrderByCreatedAtDesc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", campaign.getId().toString());
            row.put("name", campaign.getName());
            row.put("studyType", campaign.getStudyType());
            row.put("createdAt", campaign.getCreatedAt() != null ? campaign.getCreatedAt().toString() : null);
            campaignSummaries.add(row);
        }
        labSummary.put("campaigns", campaignSummaries);

        List<Map<String, Object>> classifierMaps = new ArrayList<>();
        for (ClassifierModelEntity model : classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", model.getId().toString());
            row.put("name", model.getName());
            row.put("status", model.getStatus() != null ? model.getStatus().name() : null);
            row.put("artifactPath", model.getArtifactPath());
            row.put("active", model.isActive());
            classifierMaps.add(row);
        }

        return new ExportSnapshot(
                freshUser,
                profile,
                prefs,
                pers,
                projectMaps,
                convMaps,
                messageMaps,
                docMaps,
                configMaps,
                labSummary,
                classifierMaps,
                AccountExportExclusions.build());
    }

    public record ExportSnapshot(
            UserEntity user,
            Map<String, Object> profile,
            Map<String, Object> preferences,
            Map<String, Object> personalization,
            List<Map<String, Object>> projects,
            List<Map<String, Object>> conversations,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> documents,
            List<Map<String, Object>> ragConfigSummary,
            Map<String, Object> labEvaluationSummary,
            List<Map<String, Object>> classifierModels,
            List<Map<String, Object>> exclusions) {}
}
