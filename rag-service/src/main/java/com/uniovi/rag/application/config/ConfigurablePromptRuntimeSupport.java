package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Optional;
import java.util.UUID;

/** Holder-aware helpers for {@link ConfigurablePromptResolver} at runtime. */
public final class ConfigurablePromptRuntimeSupport {

    private ConfigurablePromptRuntimeSupport() {}

    public record ScopedIds(UUID userId, UUID projectId) {}

    public static Optional<ScopedIds> fromHolder() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx == null || ctx.userId() == null || ctx.userId().isBlank()) {
            return Optional.empty();
        }
        UUID userId = UUID.fromString(ctx.userId());
        UUID projectId =
                ctx.projectId() != null && !ctx.projectId().isBlank()
                        ? UUID.fromString(ctx.projectId())
                        : null;
        return Optional.of(new ScopedIds(userId, projectId));
    }

    public static ScopedIds fromEngineContext(ExecutionContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return new ScopedIds(null, null);
        }
        return new ScopedIds(ctx.userId(), ctx.projectId());
    }

    public static String resolve(
            ConfigurablePromptResolver resolver, ConfigurablePromptGroup group, ExecutionContext ctx) {
        if (resolver == null || ctx == null) {
            return group.defaultContent();
        }
        return resolver.resolve(group, ctx.userId(), ctx.projectId());
    }

    public static String resolveSystem(
            ConfigurablePromptResolver resolver, ConfigurablePromptGroup group, ExecutionContext ctx) {
        if (resolver == null || ctx == null) {
            return group.defaultSystemContent();
        }
        return resolver.resolveSystem(group, ctx.userId(), ctx.projectId());
    }

    public static String resolveFromHolder(ConfigurablePromptResolver resolver, ConfigurablePromptGroup group) {
        if (resolver == null) {
            return group.defaultContent();
        }
        return fromHolder()
                .map(ids -> resolver.resolve(group, ids.userId(), ids.projectId()))
                .orElse(group.defaultContent());
    }

    public static String retryPolicyLine(
            ConfigurablePromptResolver resolver, ExecutionContext ctx, boolean retryAllowed) {
        String material =
                resolver != null && ctx != null
                        ? resolver.resolve(ConfigurablePromptGroup.RUNTIME_JUDGE_RETRY, ctx.userId(), ctx.projectId())
                        : ConfigurablePromptGroup.RUNTIME_JUDGE_RETRY.defaultContent();
        String line = extractRetryLine(material, retryAllowed ? "RETRY_REQUESTED" : "REJECTED_NO_RETRY");
        if (line != null) {
            return line;
        }
        return retryAllowed
                ? "If the answer is not acceptable, output RETRY_REQUESTED."
                : "If the answer is not acceptable, output REJECTED_NO_RETRY.";
    }

    private static String extractRetryLine(String material, String marker) {
        if (material == null || material.isBlank()) {
            return null;
        }
        for (String line : material.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank() && trimmed.contains(marker)) {
                return trimmed;
            }
        }
        return null;
    }

    public static String resolutionTraceMessage(
            ConfigurablePromptResolver resolver, ConfigurablePromptGroup group, ExecutionContext ctx) {
        String content = resolve(resolver, group, ctx);
        boolean custom =
                resolver != null
                        && ctx != null
                        && resolver.mergedOverrides(ctx.userId(), ctx.projectId(), null, null, null)
                                .containsKey(group.id());
        String hash =
                com.uniovi.rag.infrastructure.config.PromptBundleFingerprint.sha256Hex(content);
        String preview = hash.length() > 12 ? hash.substring(0, 12) : hash;
        return "promptGroup=" + group.id() + " source=" + (custom ? "override" : "default") + " promptHash=" + preview;
    }

    public static String resolveSystemFromHolder(
            ConfigurablePromptResolver resolver, ConfigurablePromptGroup group) {
        if (resolver == null) {
            return group.defaultSystemContent();
        }
        return fromHolder()
                .map(ids -> resolver.resolveSystem(group, ids.userId(), ids.projectId()))
                .orElse(group.defaultSystemContent());
    }
}
