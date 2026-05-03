package com.uniovi.rag.application.service.runtime.tracereplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
import static com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper.TRACE_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeTraceReplayInputLoaderTest {

    @Test
    void loads_historical_window_before_original_message_seq() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();

        MessageRepository messages = mock(MessageRepository.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        ResolvedConfigSnapshotRepository snapshots = mock(ResolvedConfigSnapshotRepository.class);
        ObjectMapper om = new ObjectMapper();
        RuntimeReplayResolvedConfigMaterializer materializer = new RuntimeReplayResolvedConfigMaterializer(om);
        ProjectAccessService access = mock(ProjectAccessService.class);

        RuntimeTraceReplayInputLoader loader =
                new RuntimeTraceReplayInputLoader(messages, conversations, snapshots, materializer, access);

        MessageEntity userMsg = Mockito.mock(MessageEntity.class);
        ConversationEntity conv = Mockito.mock(ConversationEntity.class);
        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(conv.getUser()).thenReturn(user);
        when(conv.getId()).thenReturn(convId);
        when(conv.getDocumentFilter()).thenReturn(List.of());

        MessageEntity prior = Mockito.mock(MessageEntity.class);
        when(prior.getSeq()).thenReturn(1);
        when(prior.getRole()).thenReturn(MessageRole.USER);
        when(prior.getId()).thenReturn(UUID.randomUUID());
        when(prior.getContent()).thenReturn("hi");

        when(userMsg.getConversation()).thenReturn(conv);
        when(userMsg.getSeq()).thenReturn(5);

        when(messages.findById(msgId)).thenReturn(Optional.of(userMsg));
        when(conversations.findById(convId)).thenReturn(Optional.of(conv));

        ResolvedConfigSnapshotEntity snap = buildSnapshotEntity(snapId, userId, projectId, om);
        when(snapshots.findById(snapId)).thenReturn(Optional.of(snap));

        when(messages.findByConversation_IdAndSeqLessThanAndDeletedAtIsNullOrderBySeqAsc(eq(convId), eq(5)))
                .thenReturn(List.of(prior));

        RuntimeExecutionTraceDetailDto trace =
                new RuntimeExecutionTraceDetailDto(
                        UUID.randomUUID(),
                        Instant.now(),
                        userId,
                        projectId,
                        convId,
                        msgId,
                        "c",
                        snapId,
                        "h",
                        "DirectLlmWorkflow",
                        true,
                        "OK",
                        true,
                        "OK",
                        DIRECT_WORKFLOW_ROUTE.name(),
                        false,
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        "NOT_NEEDED",
                        TRACE_SCHEMA_VERSION,
                        Map.of(),
                        List.of());

        var loaded = loader.load(userId, trace);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().memoryEligibleTurns()).hasSize(1);
        verify(access, Mockito.times(1)).requireConversationForUser(eq(userId), eq(convId));
    }

    private static ResolvedConfigSnapshotEntity buildSnapshotEntity(UUID id, UUID userId, UUID projectId, ObjectMapper om) {
        RagConfig core =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        5,
                        0.1,
                        "m",
                        "e",
                        "c",
                        "simple",
                        false,
                        100,
                        100,
                        MaterializationStrategy.CHUNK_LEVEL);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "eff",
                        new ConfigProvenance(null, null, null, List.of(), null, id),
                        core);
        ResolvedConfigSnapshot domainSnap =
                new ResolvedConfigSnapshot(
                        id,
                        Instant.now(),
                        resolved,
                        resolved.capabilitySet(),
                        resolved.compatibility(),
                        resolved.reindexImpact(),
                        resolved.effectiveSystemPrompt(),
                        resolved.provenance());
        ResolvedConfigSnapshotEntityMapper mapper = new ResolvedConfigSnapshotEntityMapper(om);
        ResolvedConfigSnapshotEntity e =
                mapper.toNewEntity(
                        resolved,
                        domainSnap,
                        ResolvedConfigSnapshotEntityMapper.ResolvedConfigSnapshotInsertContext.of(
                                userId,
                                "hash",
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(projectId)));
        e.setId(id);
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put(ResolvedConfigSnapshotEntityMapper.PROVENANCE_SCHEMA_VERSION, ResolvedConfigSnapshotEntityMapper.SNAPSHOT_SCHEMA_VERSION);
        prov.put(ResolvedConfigSnapshotEntityMapper.PROVENANCE_CREATING_USER_ID, userId.toString());
        prov.put(ResolvedConfigSnapshotEntityMapper.PROVENANCE_PROJECT_ID, projectId.toString());
        e.setProvenanceJsonb(prov);
        return e;
    }
}
