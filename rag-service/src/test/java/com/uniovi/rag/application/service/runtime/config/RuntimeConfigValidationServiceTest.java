package com.uniovi.rag.application.service.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.runtime.ExecutionWorkflow;
import com.uniovi.rag.application.service.runtime.KnowledgeRuntimeSnapshotSelector;
import com.uniovi.rag.application.service.runtime.WorkflowSelector;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeConfigValidationServiceTest {

    private ConversationRepository conversationRepository;
    private ConfigResolverService configResolverService;
    private WorkflowSelector workflowSelector;
    private KnowledgeRuntimeSnapshotSelector snapshotSelector;
    private KnowledgeSnapshotService snapshotService;
    private RuntimeConfigValidationService sut;

    @BeforeEach
    void setup() {
        conversationRepository = mock(ConversationRepository.class);
        configResolverService = mock(ConfigResolverService.class);
        workflowSelector = mock(WorkflowSelector.class);
        snapshotSelector = mock(KnowledgeRuntimeSnapshotSelector.class);
        snapshotService = mock(KnowledgeSnapshotService.class);
        sut =
                new RuntimeConfigValidationService(
                        conversationRepository,
                        new ObjectMapper(),
                        configResolverService,
                        workflowSelector,
                        snapshotSelector,
                        snapshotService);
    }

    @Test
    void validate_directLlm_ok() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        ResolvedRuntimeConfig resolved = resolvedWithCompatibility(true);
        when(configResolverService.preview(any())).thenReturn(resolved);
        when(workflowSelector.selectFromResolved(resolved)).thenReturn(new DummyWorkflow());

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(cid, null, null, Map.of("useRetrieval", false)));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.supported()).isTrue();
    }

    @Test
    void validate_chunkLevel_ok() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        ResolvedRuntimeConfig resolved = resolvedWithCompatibility(true);
        when(configResolverService.preview(any())).thenReturn(resolved);
        when(workflowSelector.selectFromResolved(resolved)).thenReturn(new DummyWorkflow());

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(
                                cid,
                                null,
                                null,
                                Map.of("useRetrieval", true, "materializationStrategy", "CHUNK_LEVEL")));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.supported()).isTrue();
    }

    @Test
    void validate_hybridSnapshot_satisfies_chunkLevelRequirement() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        KnowledgeIndexSnapshotEntity active = mock(KnowledgeIndexSnapshotEntity.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getIndexProfileHash()).thenReturn("h");
        when(active.getIndexProfileJsonb()).thenReturn(Map.of("materializationStrategy", "HYBRID", "supportsMetadata", true));
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.of(active));
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        RagConfig rag = mock(RagConfig.class);
        when(rag.useRetrieval()).thenReturn(true);
        when(rag.materializationStrategy()).thenReturn(MaterializationStrategy.CHUNK_LEVEL);
        when(rag.metadataEnabled()).thenReturn(false);
        when(configResolverService.preview(any())).thenReturn(resolvedWithRag(rag));
        when(workflowSelector.selectFromResolved(any())).thenReturn(new DummyWorkflow());

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(
                                cid,
                                null,
                                null,
                                Map.of("useRetrieval", true, "materializationStrategy", "CHUNK_LEVEL")));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.requiresReindex()).isFalse();
        assertThat(resp.indexCompatibility()).isNotNull();
        assertThat(resp.indexCompatibility().compatibleWithPreset()).isTrue();
    }

    @Test
    void validate_chunkSnapshot_doesNotSatisfy_hybridRequirement() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        KnowledgeIndexSnapshotEntity active = mock(KnowledgeIndexSnapshotEntity.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getIndexProfileHash()).thenReturn("h");
        when(active.getIndexProfileJsonb()).thenReturn(Map.of("materializationStrategy", "CHUNK_LEVEL", "supportsMetadata", true));
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.of(active));
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        RagConfig rag = mock(RagConfig.class);
        when(rag.useRetrieval()).thenReturn(true);
        when(rag.materializationStrategy()).thenReturn(MaterializationStrategy.HYBRID);
        when(rag.metadataEnabled()).thenReturn(false);
        when(configResolverService.preview(any())).thenReturn(resolvedWithRag(rag));
        when(workflowSelector.selectFromResolved(any())).thenReturn(new DummyWorkflow());

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(
                                cid,
                                null,
                                null,
                                Map.of("useRetrieval", true, "materializationStrategy", "HYBRID")));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.requiresReindex()).isTrue();
        assertThat(resp.errors()).anyMatch(e -> "MATERIALIZATION_NOT_SUPPORTED".equals(e.code()));
    }

    @Test
    void validate_metadataRequired_fails_whenSnapshotDoesNotSupportMetadata() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        KnowledgeIndexSnapshotEntity active = mock(KnowledgeIndexSnapshotEntity.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getIndexProfileHash()).thenReturn("h");
        when(active.getIndexProfileJsonb()).thenReturn(Map.of("materializationStrategy", "CHUNK_LEVEL", "supportsMetadata", false));
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.of(active));
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        RagConfig rag = mock(RagConfig.class);
        when(rag.useRetrieval()).thenReturn(true);
        when(rag.materializationStrategy()).thenReturn(MaterializationStrategy.CHUNK_LEVEL);
        when(rag.metadataEnabled()).thenReturn(true);
        when(configResolverService.preview(any())).thenReturn(resolvedWithRag(rag));
        when(workflowSelector.selectFromResolved(any())).thenReturn(new DummyWorkflow());

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(
                                cid,
                                null,
                                null,
                                Map.of("useRetrieval", true, "materializationStrategy", "CHUNK_LEVEL", "metadataEnabled", true)));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.requiresReindex()).isTrue();
        assertThat(resp.errors()).anyMatch(e -> "METADATA_SUPPORT_REQUIRED".equals(e.code()));
    }

    @Test
    void validate_reasoningEnabled_supported_whenWorkflowSelectable() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        ResolvedRuntimeConfig resolved = resolvedWithCompatibility(true);
        when(configResolverService.preview(any())).thenReturn(resolved);
        when(workflowSelector.selectFromResolved(resolved)).thenReturn(new DummyWorkflow());

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(cid, null, null, Map.of("reasoningEnabled", true)));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.supported()).isTrue();
        assertThat(resp.errors()).isEmpty();
    }

    @Test
    void validate_rankerEnabled_requiresRetrieval_invalid() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        ResolvedRuntimeConfig resolved = resolvedWithCompatibility(true);
        when(configResolverService.preview(any())).thenReturn(resolved);
        when(workflowSelector.selectFromResolved(resolved))
                .thenThrow(RagServiceException.unsupportedRuntimeConfiguration("rankerEnabled requires useRetrieval"));

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(
                                cid, null, null, Map.of("rankerEnabled", true, "useRetrieval", false)));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.supported()).isFalse();
        assertThat(resp.errors()).isNotEmpty();
    }

    @Test
    void validate_useAdvisor_requiresRetrieval_invalid() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());

        ResolvedRuntimeConfig resolved = resolvedWithCompatibility(true);
        when(configResolverService.preview(any())).thenReturn(resolved);
        when(workflowSelector.selectFromResolved(resolved))
                .thenThrow(RagServiceException.unsupportedRuntimeConfiguration("useAdvisor requires useRetrieval"));

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(
                                cid,
                                null,
                                null,
                                Map.of("useAdvisor", true, "useRetrieval", false)));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.supported()).isFalse();
    }

    private static ConversationEntity mockConversation(UUID uid, UUID cid) {
        ConversationEntity conv = mock(ConversationEntity.class);
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(uid);
        when(conv.getUser()).thenReturn(user);
        when(conv.getId()).thenReturn(cid);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(conv.getProject()).thenReturn(project);
        return conv;
    }

    private static ResolvedRuntimeConfig resolvedWithCompatibility(boolean valid) {
        RagConfig rag = mock(RagConfig.class);
        when(rag.toString()).thenReturn("rag");
        CapabilitySet caps = mock(CapabilitySet.class);
        CompatibilityResult compatibility =
                valid
                        ? CompatibilityResult.ok()
                        : new CompatibilityResult(
                                List.of(CompatibilityViolation.of("INVALID", "invalid config")),
                                List.of(),
                                List.of());
        return new ResolvedRuntimeConfig(
                rag, caps, compatibility, null, null, "", null, rag);
    }

    private static ResolvedRuntimeConfig resolvedWithRag(RagConfig rag) {
        CapabilitySet caps = mock(CapabilitySet.class);
        return new ResolvedRuntimeConfig(rag, caps, CompatibilityResult.ok(), null, null, "", null, rag);
    }

    private static final class DummyWorkflow implements ExecutionWorkflow {
        @Override
        public RagExecutionResult execute(ExecutionContext ctx) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public String workflowName() {
            return "DummyWorkflow";
        }
    }
}

