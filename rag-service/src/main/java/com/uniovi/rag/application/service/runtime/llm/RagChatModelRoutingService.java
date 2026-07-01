package com.uniovi.rag.application.service.runtime.llm;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogDefaults;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.catalog.LlmModelRoleCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelRoleResolver;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** Blocks or falls back when a model lacks the role required for a RAG LLM call. */
@Service
public class RagChatModelRoutingService {

    private static final Logger log = LoggerFactory.getLogger(RagChatModelRoutingService.class);

    private final LlmModelCatalogPort modelCatalog;

    public RagChatModelRoutingService(LlmModelCatalogPort modelCatalog) {
        this.modelCatalog = Objects.requireNonNull(modelCatalog, "modelCatalog");
    }

    public record RoutedChatModel(String model, boolean fallbackApplied, @Nullable String requestedModel) {}

    public RoutedChatModel resolvePrimary(LlmProvider provider, String requestedModel, @Nullable ExecutionContext ctx) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return new RoutedChatModel(requestedModel, false, requestedModel);
        }
        String trimmed = requestedModel.trim();
        if (LlmModelRoleResolver.supportsPrimaryChat(trimmed, LlmModelCapability.CHAT)) {
            logDecision(ctx, trimmed, "PRIMARY", "ALLOWED", trimmed);
            return new RoutedChatModel(trimmed, false, trimmed);
        }
        Optional<String> fallback = resolveSafePrimaryFallback(provider);
        if (fallback.isPresent()) {
            logFallback(ctx, trimmed, fallback.get(), "PRIMARY_MODEL_CAPABILITY_MISMATCH");
            return new RoutedChatModel(fallback.get(), true, trimmed);
        }
        logBlocked(ctx, trimmed, "PRIMARY", LlmModelRoleCapability.CHAT_PRIMARY.name(), trimmed);
        throw LlmConfigurationException.invalidField(
                provider,
                "chatModel",
                trimmed,
                null,
                LlmModelReasonCodes.format(
                        LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED,
                        "Model '"
                                + trimmed
                                + "' cannot be used as PRIMARY chat model (capability mismatch: "
                                + LlmModelRoleResolver.primaryRoleLabel(trimmed, LlmModelCapability.CHAT)
                                + ")"));
    }

    public String resolveSecondary(LlmProvider provider, String requestedModel, @Nullable ExecutionContext ctx) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return requestedModel;
        }
        String trimmed = requestedModel.trim();
        if (LlmModelRoleResolver.supportsSecondaryChat(trimmed, LlmModelCapability.CHAT)) {
            logDecision(ctx, trimmed, "SECONDARY", "ALLOWED", trimmed);
            return trimmed;
        }
        Optional<String> fallback = resolveSafePrimaryFallback(provider);
        if (fallback.isPresent()) {
            logFallback(ctx, trimmed, fallback.get(), "SECONDARY_MODEL_CAPABILITY_MISMATCH");
            return fallback.get();
        }
        logBlocked(ctx, trimmed, "SECONDARY", LlmModelRoleCapability.CHAT_SECONDARY.name(), trimmed);
        throw LlmConfigurationException.invalidField(
                provider,
                "chatModel",
                trimmed,
                null,
                LlmModelReasonCodes.format(
                        LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED,
                        "Model '" + trimmed + "' cannot be used as SECONDARY chat model"));
    }

    private Optional<String> resolveSafePrimaryFallback(LlmProvider provider) {
        if (provider == null) {
            return Optional.empty();
        }
        LlmCatalogDefaults defaults = modelCatalog.resolveSystemDefaults(provider);
        if (defaults.defaultChatModel() != null
                && !defaults.defaultChatModel().isBlank()
                && LlmModelRoleResolver.supportsPrimaryChat(
                        defaults.defaultChatModel(), LlmModelCapability.CHAT)) {
            return Optional.of(defaults.defaultChatModel().trim());
        }
        List<LlmCatalogEntry> chatModels =
                modelCatalog.listConfigured(
                        LlmCatalogQuery.forProviderAndCapability(provider, LlmModelCapability.CHAT));
        return chatModels.stream()
                .map(LlmCatalogEntry::modelName)
                .filter(name -> LlmModelRoleResolver.supportsPrimaryChat(name, LlmModelCapability.CHAT))
                .findFirst();
    }

    private static void logDecision(
            @Nullable ExecutionContext ctx,
            String model,
            String modelRole,
            String decision,
            String effectiveModel) {
        log.info(
                "RAG_MODEL_ROUTING_DECISION traceId={} conversationId={} presetId={} modelRole={} decision={} requestedModel={} effectiveModel={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                modelRole,
                decision,
                safe(model),
                safe(effectiveModel));
    }

    private static void logFallback(
            @Nullable ExecutionContext ctx, String fromModel, String toModel, String reason) {
        log.warn(
                "RAG_MODEL_ROUTING_FALLBACK traceId={} conversationId={} presetId={} from={} to={} reason={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                safe(fromModel),
                safe(toModel),
                reason);
    }

    private static void logBlocked(
            @Nullable ExecutionContext ctx,
            String requestedModel,
            String modelRole,
            String required,
            String actual) {
        log.warn(
                "RAG_MODEL_ROUTING_BLOCKED traceId={} conversationId={} presetId={} requestedModel={} modelRole={} reason=MODEL_CAPABILITY_MISMATCH required={} actual={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                safe(requestedModel),
                modelRole,
                required,
                LlmModelRoleResolver.primaryRoleLabel(actual, LlmModelCapability.CHAT));
    }

    private static String traceId(@Nullable ExecutionContext ctx) {
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId() : "";
    }

    private static String conversationId(@Nullable ExecutionContext ctx) {
        return ctx != null && ctx.conversationId() != null ? ctx.conversationId().toString() : "";
    }

    private static String presetId(@Nullable ExecutionContext ctx) {
        if (ctx == null
                || ctx.resolved() == null
                || ctx.resolved().provenance() == null
                || ctx.resolved().provenance().presetId() == null) {
            return "";
        }
        return ctx.resolved().provenance().presetId().toString();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
