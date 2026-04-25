package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationHistoryLoaderTest {

    @Test
    void loadEligibleHistory_returnsEmpty_whenNoConversationScope() {
        MessageRepository repo = mock(MessageRepository.class);
        ConversationHistoryLoader loader = new ConversationHistoryLoader(repo);

        ExecutionContext ctx = ctx(null, Optional.empty());
        assertThat(loader.loadEligibleHistory(ctx)).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void loadEligibleHistory_excludesOriginatingUserMessageId_andPreservesSeqOrdering() {
        MessageRepository repo = mock(MessageRepository.class);
        ConversationHistoryLoader loader = new ConversationHistoryLoader(repo);

        UUID conversationId = UUID.randomUUID();
        UUID excludeId = UUID.randomUUID();

        MessageEntity m1 = msg(UUID.randomUUID(), 1, MessageRole.USER, "a", null);
        MessageEntity m2 = msg(excludeId, 2, MessageRole.USER, "exclude", null);
        MessageEntity m3 = msg(UUID.randomUUID(), 3, MessageRole.ASSISTANT, "b", null);

        when(repo.findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(conversationId))
                .thenReturn(List.of(m1, m2, m3));

        ExecutionContext ctx = ctx(conversationId, Optional.of(excludeId));
        List<ConversationMemoryTurn> out = loader.loadEligibleHistory(ctx);

        assertThat(out).extracting(ConversationMemoryTurn::seq).containsExactly(1, 3);
        assertThat(out).extracting(ConversationMemoryTurn::content).containsExactly("a", "b");
    }

    private static MessageEntity msg(UUID id, int seq, MessageRole role, String content, Instant deletedAt) {
        MessageEntity e = new MessageEntity();
        e.setId(id);
        e.setSeq(seq);
        e.setRole(role);
        e.setContent(content);
        e.setDeletedAt(deletedAt);
        return e;
    }

    private static ExecutionContext ctx(UUID conversationId, Optional<UUID> originatingUserMessageId) {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        false,
                        5,
                        0.2,
                        "l",
                        "e",
                        "c",
                        "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        String q = "q";
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                conversationId,
                q,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                q,
                q,
                Optional.empty(),
                conversationId == null ? ConversationMemoryOutcome.NO_CONVERSATION_SCOPE : ConversationMemoryOutcome.NO_HISTORY_AVAILABLE,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                originatingUserMessageId,
                false,
                com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}

