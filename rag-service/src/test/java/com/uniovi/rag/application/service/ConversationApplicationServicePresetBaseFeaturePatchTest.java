package com.uniovi.rag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.chat.IndexAwareChatPresetDefaultService;
import com.uniovi.rag.application.service.chat.PresetBaseFeatureSupport;
import com.uniovi.rag.application.service.chat.RuntimeConfigurationInvalidException;
import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.preset.PresetService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeSnapshotCapabilitiesDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationApplicationServicePresetBaseFeaturePatchTest {

    private static final UUID P3_PRESET_ID =
            ExperimentalPresetCanonicalCatalog.productPresetId(RagExperimentalPresetCode.P3);
    private static final UUID P7_PRESET_ID =
            ExperimentalPresetCanonicalCatalog.productPresetId(RagExperimentalPresetCode.P7);

    private ProjectAccessService projectAccessService;
    private ConversationRepository conversationRepository;
    private RuntimeConfigValidationService runtimeConfigValidationService;
    private ChatPresetDefaults chatPresetDefaults;

    private ConversationApplicationService sut;
    private ConversationEntity conv;
    private UUID userId;
    private UUID conversationId;
    private Map<String, Object> storedOverride = new LinkedHashMap<>();

    @BeforeEach
    void setup() {
        storedOverride = new LinkedHashMap<>();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        projectAccessService = mock(ProjectAccessService.class);
        conversationRepository = mock(ConversationRepository.class);
        runtimeConfigValidationService = mock(RuntimeConfigValidationService.class);
        chatPresetDefaults = mock(ChatPresetDefaults.class);

        sut =
                new ConversationApplicationService(
                        projectAccessService,
                        conversationRepository,
                        mock(MessageRepository.class),
                        mock(KnowledgeDocumentRepository.class),
                        mock(PresetService.class),
                        chatPresetDefaults,
                        mock(IndexAwareChatPresetDefaultService.class),
                        mock(LabExperimentalPresetCatalogService.class),
                        runtimeConfigValidationService);

        UUID pid = UUID.randomUUID();

        conv = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        when(conv.getProject()).thenReturn(project);
        when(conv.getRuntimeOverride()).thenAnswer(inv -> storedOverride);
        doAnswer(
                        inv -> {
                            storedOverride = new LinkedHashMap<>(inv.getArgument(0));
                            return null;
                        })
                .when(conv)
                .setRuntimeOverride(any());

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(P3_PRESET_ID);
        when(conv.getPreset()).thenReturn(preset);

        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);
        when(chatPresetDefaults.effectivePresetIdForApi(P3_PRESET_ID)).thenReturn(P3_PRESET_ID);
    }

    @Test
    void patchConversation_rejectsDisablingUseRetrievalForChunkRag() {
        Map<String, Object> p3Base =
                new LinkedHashMap<>(
                        ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3));
        RuntimeIndexCompatibilityDto idx = chunkIndex();

        when(runtimeConfigValidationService.validate(eq(userId), any()))
                .thenReturn(validResponse(p3Base, idx));

        assertThatThrownBy(
                        () ->
                                sut.patchConversation(
                                        userId,
                                        conversationId,
                                        new PatchConversationRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                Map.of("useRetrieval", false),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(RuntimeConfigurationInvalidException.class)
                .satisfies(
                        ex ->
                                assertThat(((RuntimeConfigurationInvalidException) ex).code())
                                        .isEqualTo(PresetBaseFeatureSupport.PRESET_BASE_FEATURE_LOCKED));

        verify(conv, never()).setRuntimeOverride(any());
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void patchConversation_rejectsDisablingToolsForDeterministicToolsPreset() {
        RagPresetEntity p7Preset = mock(RagPresetEntity.class);
        when(p7Preset.getId()).thenReturn(P7_PRESET_ID);
        when(conv.getPreset()).thenReturn(p7Preset);
        when(chatPresetDefaults.effectivePresetIdForApi(P7_PRESET_ID)).thenReturn(P7_PRESET_ID);

        Map<String, Object> p7Base =
                new LinkedHashMap<>(
                        ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P7));
        RuntimeIndexCompatibilityDto idx = chunkIndex();

        when(runtimeConfigValidationService.validate(eq(userId), any()))
                .thenReturn(validResponse(p7Base, idx));

        assertThatThrownBy(
                        () ->
                                sut.patchConversation(
                                        userId,
                                        conversationId,
                                        new PatchConversationRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                Map.of("toolsEnabled", false),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(RuntimeConfigurationInvalidException.class)
                .satisfies(
                        ex -> {
                            RuntimeConfigurationInvalidException rce = (RuntimeConfigurationInvalidException) ex;
                            assertThat(rce.code()).isEqualTo(PresetBaseFeatureSupport.PRESET_BASE_FEATURE_LOCKED);
                        });

        verify(conv, never()).setRuntimeOverride(any());
    }

    @Test
    void patchConversation_allowsRetrievalNumericOverrides() {
        Map<String, Object> p3Base =
                new LinkedHashMap<>(
                        ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3));
        RuntimeIndexCompatibilityDto idx = chunkIndex();

        when(runtimeConfigValidationService.validate(eq(userId), any()))
                .thenAnswer(
                        inv -> {
                            RuntimeConfigValidateRequest req = inv.getArgument(1);
                            Map<String, Object> effective = new LinkedHashMap<>(p3Base);
                            if (req.overrides() != null) {
                                effective.putAll(req.overrides());
                            }
                            return validResponse(effective, idx);
                        });
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.patchConversation(
                userId,
                conversationId,
                new PatchConversationRequest(
                        null,
                        null,
                        null,
                        null,
                        Map.of("topK", 12, "similarityThreshold", 0.55),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        verify(conversationRepository).save(conv);
        assertThat(storedOverride).containsEntry("topK", 12).containsEntry("similarityThreshold", 0.55);
    }

    private static RuntimeConfigValidateResponse validResponse(
            Map<String, Object> effective, RuntimeIndexCompatibilityDto idx) {
        return new RuntimeConfigValidateResponse(
                true, true, effective, List.of(), List.of(), "wf", idx, false);
    }

    private static RuntimeIndexCompatibilityDto chunkIndex() {
        return new RuntimeIndexCompatibilityDto(
                UUID.randomUUID(),
                null,
                "hash",
                Map.of("materializationStrategy", "CHUNK_LEVEL"),
                true,
                new RuntimeSnapshotCapabilitiesDto("CHUNK_LEVEL", true, "emb", 400, 40),
                null,
                true,
                "COMPATIBLE");
    }
}
