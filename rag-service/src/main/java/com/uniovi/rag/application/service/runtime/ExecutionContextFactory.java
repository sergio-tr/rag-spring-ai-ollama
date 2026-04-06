package com.uniovi.rag.application.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.domain.config.EffectiveModelPolicy;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.infrastructure.observability.TraceMdcBridge;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import io.micrometer.tracing.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sole constructor of {@link ExecutionContext} for orchestrated turns.
 */
@Service
public class ExecutionContextFactory {

    private final RuntimeConfigResolutionService runtimeConfigResolutionService;
    private final KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;
    private final ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    private final ModelCatalogPort modelCatalogPort;
    private final Tracer tracer;

    public ExecutionContextFactory(
            RuntimeConfigResolutionService runtimeConfigResolutionService,
            KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector,
            ChatScopedRagConfigResolver chatScopedRagConfigResolver,
            ModelCatalogPort modelCatalogPort,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Tracer tracer) {
        this.runtimeConfigResolutionService = runtimeConfigResolutionService;
        this.knowledgeRuntimeSnapshotSelector = knowledgeRuntimeSnapshotSelector;
        this.chatScopedRagConfigResolver = chatScopedRagConfigResolver;
        this.modelCatalogPort = modelCatalogPort;
        this.tracer = tracer;
    }

    public ExecutionContext buildForChatMessage(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String rawUserQuery,
            List<String> documentFilter,
            String chatModelOverride) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        JsonNode merged =
                conversationId != null
                        ? chatScopedRagConfigResolver.mergedConversationConfigAsJson(conversationId)
                        : null;
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        userId, projectId, merged, correlationId);
        KnowledgeSnapshotSelection snapshots =
                knowledgeRuntimeSnapshotSelector.select(projectId, conversationId);
        List<String> filter = copyDocumentFilter(documentFilter);
        return new ExecutionContext(
                userId,
                projectId,
                conversationId,
                rawUserQuery != null ? rawUserQuery : "",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                resolved.effectiveSystemPrompt(),
                snapshots,
                Optional.empty(),
                Optional.empty(),
                correlationId,
                filter,
                model);
    }

    public ExecutionContext buildForLegacyHttp(String rawUserQuery, String chatModelOverride) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        null, null, null, correlationId);
        KnowledgeSnapshotSelection snapshots = knowledgeRuntimeSnapshotSelector.select(null, null);
        return new ExecutionContext(
                null,
                null,
                null,
                rawUserQuery != null ? rawUserQuery : "",
                RuntimeOperationKind.LEGACY_HTTP,
                resolved,
                resolved.effectiveSystemPrompt(),
                snapshots,
                Optional.empty(),
                Optional.empty(),
                correlationId,
                List.of(RagExecutionContext.ALL_DOCUMENTS),
                model);
    }

    public ExecutionContext buildForLabProcess(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String rawUserQuery,
            List<String> documentFilter,
            String chatModelOverride) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        JsonNode merged =
                conversationId != null
                        ? chatScopedRagConfigResolver.mergedConversationConfigAsJson(conversationId)
                        : null;
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        userId, projectId, merged, correlationId);
        KnowledgeSnapshotSelection snapshots =
                knowledgeRuntimeSnapshotSelector.select(projectId, conversationId);
        return new ExecutionContext(
                userId,
                projectId,
                conversationId,
                rawUserQuery != null ? rawUserQuery : "",
                RuntimeOperationKind.LAB_PROCESS,
                resolved,
                resolved.effectiveSystemPrompt(),
                snapshots,
                Optional.empty(),
                Optional.empty(),
                correlationId,
                copyDocumentFilter(documentFilter),
                model);
    }

    private Optional<String> validateAndNormalizeChatModel(String chatModelOverride) {
        if (chatModelOverride == null || chatModelOverride.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    EffectiveModelPolicy.validateChatModelOverride(
                            chatModelOverride, modelCatalogPort.allowedLlmNamesInGovernance()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static List<String> copyDocumentFilter(List<String> documentFilter) {
        if (documentFilter == null || documentFilter.isEmpty()) {
            return List.of(RagExecutionContext.ALL_DOCUMENTS);
        }
        return List.copyOf(documentFilter);
    }
}
