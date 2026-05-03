package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    public AccountExportSnapshotLoader(
            UserRepository userRepository,
            UserPreferencesRepository userPreferencesRepository,
            UserPersonalizationRepository userPersonalizationRepository,
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.userRepository = userRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.userPersonalizationRepository = userPersonalizationRepository;
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Transactional(readOnly = true)
    public ExportSnapshot load(UUID userId) {
        UserEntity freshUser = userRepository.findById(userId).orElseThrow();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", 1);
        manifest.put("exportedAt", Instant.now().toString());
        manifest.put("userId", userId.toString());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", freshUser.getEmail());
        profile.put("name", freshUser.getName());

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

        return new ExportSnapshot(freshUser, manifest, profile, prefs, pers, projectMaps, convMaps, docMaps);
    }

    public record ExportSnapshot(
            UserEntity user,
            Map<String, Object> manifest,
            Map<String, Object> profile,
            Map<String, Object> preferences,
            Map<String, Object> personalization,
            List<Map<String, Object>> projects,
            List<Map<String, Object>> conversations,
            List<Map<String, Object>> documents) {}
}
