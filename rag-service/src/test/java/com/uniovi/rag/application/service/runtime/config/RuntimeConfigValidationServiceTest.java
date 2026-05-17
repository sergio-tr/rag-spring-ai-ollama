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
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
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

    private static final String USE_RETRIEVAL = "useRetrieval";
    private static final String MATERIALIZATION_STRATEGY = "materializationStrategy";
    private static final String CHUNK_LEVEL = "CHUNK_LEVEL";
    private static final String SUPPORTS_METADATA = "supportsMetadata";

    private ConversationRepository conversationRepository;
    private ConfigResolverService configResolverService;
    private WorkflowSelector workflowSelector;
    private KnowledgeRuntimeSnapshotSelector snapshotSelector;
    private KnowledgeSnapshotService snapshotService;
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    private RuntimeConfigValidationService sut;

    @BeforeEach
    void setup() {
        conversationRepository = mock(ConversationRepository.class);
        configResolverService = mock(ConfigResolverService.class);
        workflowSelector = mock(WorkflowSelector.class);
        snapshotSelector = mock(KnowledgeRuntimeSnapshotSelector.class);
        snapshotService = mock(KnowledgeSnapshotService.class);
        knowledgeDocumentRepository = mock(KnowledgeDocumentRepository.class);
        sut =
                new RuntimeConfigValidationService(
                        conversationRepository,
                        new ObjectMapper(),
                        configResolverService,
                        workflowSelector,
                        snapshotSelector,
                        snapshotService,
                        knowledgeDocumentRepository);
    }

    @Test
    void validateDirectLlmOk() {
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
                        new RuntimeConfigValidateRequest(cid, null, null, Map.of(USE_RETRIEVAL, false)));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.supported()).isTrue();
    }

    @Test
    void validateChunkLevelOk() {
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
                                Map.of(USE_RETRIEVAL, true, MATERIALIZATION_STRATEGY, CHUNK_LEVEL)));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.supported()).isTrue();
    }

    @Test
    void validateHybridSnapshotSatisfiesChunkLevelRequirement() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        KnowledgeIndexSnapshotEntity active = mock(KnowledgeIndexSnapshotEntity.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getIndexProfileHash()).thenReturn("h");
        when(active.getIndexProfileJsonb()).thenReturn(Map.of(MATERIALIZATION_STRATEGY, "HYBRID", SUPPORTS_METADATA, true));
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
                                Map.of(USE_RETRIEVAL, true, MATERIALIZATION_STRATEGY, CHUNK_LEVEL)));
        assertThat(resp.valid()).isTrue();
        assertThat(resp.requiresReindex()).isFalse();
        assertThat(resp.indexCompatibility()).isNotNull();
        assertThat(resp.indexCompatibility().compatibleWithPreset()).isTrue();
    }

    @Test
    void validateChunkSnapshotDoesNotSatisfyHybridRequirement() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        KnowledgeIndexSnapshotEntity active = mock(KnowledgeIndexSnapshotEntity.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getIndexProfileHash()).thenReturn("h");
        when(active.getIndexProfileJsonb()).thenReturn(Map.of(MATERIALIZATION_STRATEGY, CHUNK_LEVEL, SUPPORTS_METADATA, true));
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
                                Map.of(USE_RETRIEVAL, true, MATERIALIZATION_STRATEGY, "HYBRID")));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.requiresReindex()).isTrue();
        assertThat(resp.errors()).anyMatch(e -> "MATERIALIZATION_NOT_SUPPORTED".equals(e.code()));
    }

    @Test
    void validateNoActiveSnapshotWithNoReadyDocumentsIsWarningNotReindexError() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());
        when(knowledgeDocumentRepository.countByProject_IdAndStatus(any(), any())).thenReturn(0L);

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
                                Map.of(USE_RETRIEVAL, true, MATERIALIZATION_STRATEGY, CHUNK_LEVEL)));

        assertThat(resp.valid()).isTrue();
        assertThat(resp.supported()).isTrue();
        assertThat(resp.requiresReindex()).isFalse();
        assertThat(resp.errors()).isEmpty();
        assertThat(resp.warnings()).anyMatch(e -> "NO_ACTIVE_INDEX".equals(e.code()));
    }

    @Test
    void validateNoActiveSnapshotWithReadyDocumentsRequiresReindex() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        when(snapshotService.findActiveProjectSnapshot(any())).thenReturn(Optional.empty());
        when(snapshotService.findActiveConversationSnapshot(any())).thenReturn(Optional.empty());
        when(knowledgeDocumentRepository.countByProject_IdAndStatus(any(), any())).thenReturn(1L);

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
                                Map.of(USE_RETRIEVAL, true, MATERIALIZATION_STRATEGY, CHUNK_LEVEL)));

        assertThat(resp.valid()).isFalse();
        assertThat(resp.supported()).isFalse();
        assertThat(resp.requiresReindex()).isTrue();
        assertThat(resp.errors()).anyMatch(e -> "NO_ACTIVE_INDEX".equals(e.code()));
    }

    @Test
    void validateMetadataRequiredFailsWhenSnapshotDoesNotSupportMetadata() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));
        when(snapshotSelector.select(any(), any())).thenReturn(KnowledgeSnapshotSelection.empty());
        KnowledgeIndexSnapshotEntity active = mock(KnowledgeIndexSnapshotEntity.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getIndexProfileHash()).thenReturn("h");
        when(active.getIndexProfileJsonb()).thenReturn(Map.of(MATERIALIZATION_STRATEGY, CHUNK_LEVEL, SUPPORTS_METADATA, false));
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
                                Map.of(USE_RETRIEVAL, true, MATERIALIZATION_STRATEGY, CHUNK_LEVEL, "metadataEnabled", true)));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.requiresReindex()).isTrue();
        assertThat(resp.errors()).anyMatch(e -> "METADATA_SUPPORT_REQUIRED".equals(e.code()));
    }

    @Test
    void validateReasoningEnabledSupportedWhenWorkflowSelectable() {
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
    void validateRankerEnabledRequiresRetrievalInvalid() {
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
                                cid, null, null, Map.of("rankerEnabled", true, USE_RETRIEVAL, false)));
        assertThat(resp.valid()).isFalse();
        assertThat(resp.supported()).isFalse();
        assertThat(resp.errors()).isNotEmpty();
    }

    @Test
    void validateUseAdvisorRequiresRetrievalInvalid() {
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
                                Map.of("useAdvisor", true, USE_RETRIEVAL, false)));
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

