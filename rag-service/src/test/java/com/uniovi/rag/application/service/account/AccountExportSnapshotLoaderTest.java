package com.uniovi.rag.application.service.account;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
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
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPersonalizationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPreferencesEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountExportSnapshotLoaderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private UserPersonalizationRepository userPersonalizationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RagConfigurationRepository ragConfigurationRepository;

    @Mock
    private EvaluationDatasetRepository evaluationDatasetRepository;

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationCampaignRepository evaluationCampaignRepository;

    @Mock
    private ClassifierModelRepository classifierModelRepository;

    @InjectMocks
    private AccountExportSnapshotLoader loader;

    @Test
    void load_buildsSnapshot_fromRepositories() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getEmail()).thenReturn("u@example.com");
        when(user.getName()).thenReturn("User");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());
        when(userPersonalizationRepository.findById(userId)).thenReturn(Optional.empty());

        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        when(project.getName()).thenReturn("P");
        when(project.getDescription()).thenReturn("d");
        when(project.getProjectPrompt()).thenReturn("pp");
        when(project.getColorHex()).thenReturn("#fff");
        when(project.getIconKey()).thenReturn("folder");
        when(projectRepository.findByOwner_IdOrderByUpdatedAtDesc(eq(userId), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(project)));

        ConversationEntity conv = Mockito.mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(convId);
        when(conv.getProject()).thenReturn(project);
        when(conv.getTitle()).thenReturn("Chat");
        when(conversationRepository.findByProject_IdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of(conv));

        KnowledgeDocumentEntity doc = Mockito.mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(docId);
        when(doc.getProject()).thenReturn(project);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getFileName()).thenReturn("a.pdf");
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getUploadedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(doc.getByteSize()).thenReturn(42L);
        when(doc.getStorageUri()).thenReturn("file:///tmp/a.pdf");
        when(knowledgeDocumentRepository.findAllByProjectOwner_Id(userId)).thenReturn(List.of(doc));

        when(messageRepository.findAllByConversationUser_Id(userId)).thenReturn(List.of());
        when(ragConfigurationRepository.findByUser_IdOrderByUpdatedAtDesc(userId)).thenReturn(List.of());
        when(evaluationDatasetRepository.findByOwner_IdOrderByUploadedAtDesc(userId)).thenReturn(List.of());
        when(evaluationRunRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(evaluationCampaignRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of());

        AccountExportSnapshotLoader.ExportSnapshot snap = loader.load(userId);

        assertThat(snap.user()).isSameAs(user);
        assertThat(snap.profile()).containsEntry("email", "u@example.com");
        assertThat(snap.projects()).hasSize(1);
        assertThat(snap.projects().getFirst()).containsEntry("id", projectId.toString());
        assertThat(snap.conversations()).hasSize(1);
        assertThat(snap.documents()).hasSize(1);
        assertThat(snap.documents().getFirst()).containsEntry("corpusScope", "PROJECT_SHARED");
        assertThat(snap.exclusions()).isNotEmpty();
    }

    @Test
    void load_mapsOptionalJsonBlobs_whenPresent() {
        UUID userId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getEmail()).thenReturn("e@e.e");
        when(user.getName()).thenReturn("N");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var prefRow = Mockito.mock(UserPreferencesEntity.class);
        when(prefRow.getPreferences()).thenReturn(Map.of("theme", "dark"));
        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(prefRow));

        var persRow = Mockito.mock(UserPersonalizationEntity.class);
        when(persRow.getPersonalization()).thenReturn(null);
        when(userPersonalizationRepository.findById(userId)).thenReturn(Optional.of(persRow));

        when(projectRepository.findByOwner_IdOrderByUpdatedAtDesc(eq(userId), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of()));
        when(messageRepository.findAllByConversationUser_Id(userId)).thenReturn(List.of());
        when(ragConfigurationRepository.findByUser_IdOrderByUpdatedAtDesc(userId)).thenReturn(List.of());
        when(evaluationDatasetRepository.findByOwner_IdOrderByUploadedAtDesc(userId)).thenReturn(List.of());
        when(evaluationRunRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(evaluationCampaignRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of());

        AccountExportSnapshotLoader.ExportSnapshot snap = loader.load(userId);

        assertThat(snap.preferences()).containsEntry("theme", "dark");
        assertThat(snap.personalization()).isEmpty();
    }
}
