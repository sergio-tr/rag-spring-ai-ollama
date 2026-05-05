package com.uniovi.rag.application.service.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.ExecutionWorkflow;
import com.uniovi.rag.application.service.runtime.WorkflowSelector;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
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
    private RuntimeConfigValidationService sut;

    @BeforeEach
    void setup() {
        conversationRepository = mock(ConversationRepository.class);
        configResolverService = mock(ConfigResolverService.class);
        workflowSelector = mock(WorkflowSelector.class);
        sut =
                new RuntimeConfigValidationService(
                        conversationRepository, new ObjectMapper(), configResolverService, workflowSelector);
    }

    @Test
    void validate_directLlm_ok() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));

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
    void validate_reasoningEnabled_notSupported() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity conv = mockConversation(uid, cid);
        when(conversationRepository.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));

        ResolvedRuntimeConfig resolved = resolvedWithCompatibility(true);
        when(configResolverService.preview(any())).thenReturn(resolved);
        when(workflowSelector.selectFromResolved(resolved))
                .thenThrow(RagServiceException.unsupportedRuntimeConfiguration("advanced runtime capabilities are not implemented"));

        var resp =
                sut.validate(
                        uid,
                        new RuntimeConfigValidateRequest(cid, null, null, Map.of("reasoningEnabled", true)));
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

