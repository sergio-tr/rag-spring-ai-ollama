package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only linked inputs for replay (messages, conversation, pinned resolved config snapshot, historical memory window).
 */
@Service
public class RuntimeTraceReplayInputLoader {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    private final RuntimeReplayResolvedConfigMaterializer resolvedConfigMaterializer;
    private final ProjectAccessService projectAccessService;

    public RuntimeTraceReplayInputLoader(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository,
            RuntimeReplayResolvedConfigMaterializer resolvedConfigMaterializer,
            ProjectAccessService projectAccessService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.resolvedConfigSnapshotRepository = resolvedConfigSnapshotRepository;
        this.resolvedConfigMaterializer = resolvedConfigMaterializer;
        this.projectAccessService = projectAccessService;
    }

    /**
     * @return empty when the pinned resolved snapshot row cannot be materialized (caller maps to unsupported outcome).
     */
    @Transactional(readOnly = true)
    public Optional<ReplayLoadedInputs> load(UUID userId, RuntimeExecutionTraceDetailDto trace) {
        projectAccessService.requireConversationForUser(userId, trace.conversationId());

        MessageEntity userMessage =
                messageRepository
                        .findById(trace.messageId())
                        .filter(m -> m.getConversation() != null && trace.conversationId().equals(m.getConversation().getId()))
                        .orElseThrow(() -> new NotFoundException("message not found"));

        ConversationEntity conversation =
                conversationRepository
                        .findById(trace.conversationId())
                        .filter(c -> c.getUser() != null && userId.equals(c.getUser().getId()))
                        .orElseThrow(() -> new NotFoundException("conversation not found"));

        ResolvedConfigSnapshotEntity snapshotEntity =
                resolvedConfigSnapshotRepository
                        .findById(trace.resolvedConfigSnapshotId())
                        .orElseThrow(() -> new NotFoundException("resolved config snapshot not found"));
        validateSnapshotOwnership(snapshotEntity, userId, trace.projectId());

        Optional<ResolvedRuntimeConfig> resolvedOpt = resolvedConfigMaterializer.materialize(snapshotEntity);
        if (resolvedOpt.isEmpty()) {
            return Optional.empty();
        }
        ResolvedRuntimeConfig resolved = resolvedOpt.get();

        List<MessageEntity> prior =
                messageRepository.findByConversation_IdAndSeqLessThanAndDeletedAtIsNullOrderBySeqAsc(
                        trace.conversationId(), userMessage.getSeq());
        List<ConversationMemoryTurn> memoryTurns = toMemoryTurns(prior);

        List<String> documentFilter = copyDocumentFilter(conversation.getDocumentFilter());
        return Optional.of(new ReplayLoadedInputs(resolved, userMessage, conversation, memoryTurns, documentFilter));
    }

    private static List<ConversationMemoryTurn> toMemoryTurns(List<MessageEntity> rows) {
        List<ConversationMemoryTurn> out = new ArrayList<>();
        for (MessageEntity m : rows) {
            if (m == null) {
                continue;
            }
            MessageRole role = m.getRole();
            if (role != MessageRole.USER && role != MessageRole.ASSISTANT) {
                continue;
            }
            out.add(new ConversationMemoryTurn(m.getId(), m.getSeq(), role, m.getContent()));
        }
        return List.copyOf(out);
    }

    private static List<String> copyDocumentFilter(List<String> documentFilter) {
        if (documentFilter == null || documentFilter.isEmpty()) {
            return List.of(RagExecutionContext.ALL_DOCUMENTS);
        }
        return List.copyOf(documentFilter);
    }

    private static void validateSnapshotOwnership(ResolvedConfigSnapshotEntity entity, UUID userId, UUID projectId) {
        UUID owner = readCreatingUserId(entity);
        if (owner == null || !owner.equals(userId)) {
            throw new NotFoundException("resolved config snapshot not found");
        }
        UUID provProject = readProjectId(entity);
        if (provProject == null || !provProject.equals(projectId)) {
            throw new NotFoundException("resolved config snapshot not found");
        }
    }

    private static UUID readCreatingUserId(ResolvedConfigSnapshotEntity entity) {
        if (entity.getProvenanceJsonb() == null) {
            return null;
        }
        Object raw = entity.getProvenanceJsonb().get(ResolvedConfigSnapshotEntityMapper.PROVENANCE_CREATING_USER_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID readProjectId(ResolvedConfigSnapshotEntity entity) {
        if (entity.getProvenanceJsonb() == null) {
            return null;
        }
        Object raw = entity.getProvenanceJsonb().get(ResolvedConfigSnapshotEntityMapper.PROVENANCE_PROJECT_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record ReplayLoadedInputs(
            ResolvedRuntimeConfig resolved,
            MessageEntity userMessage,
            ConversationEntity conversation,
            List<ConversationMemoryTurn> memoryEligibleTurns,
            List<String> documentFilter) {}
}
