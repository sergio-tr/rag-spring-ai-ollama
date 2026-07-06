package com.uniovi.rag.application.service.runtime.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.application.service.runtime.optimization.DeterministicQueryRewriteShortcuts;
import com.uniovi.rag.application.service.runtime.optimization.RagLlmCallBudgetPolicy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultStructuredQueryRewriter implements StructuredQueryRewriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private final ConfigurablePromptResolver promptResolver;

    public DefaultStructuredQueryRewriter(
            ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor, ConfigurablePromptResolver promptResolver) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
        this.promptResolver = promptResolver;
    }

    @Override
    public StructuredRewriteResult rewrite(
            ExecutionContext ctx,
            NormalizedQuery normalized,
            String classifierLabel,
            Optional<QueryType> classifierQueryType,
            ClassifierStatus classifierStatus,
            EntityExtractionResult entities) {

        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.nerEnabled()) {
            return StructuredRewriteResult.identityDisabled(normalized.normalizedText(), "nerEnabled=false");
        }

        Optional<StructuredRewriteResult> deterministic =
                DeterministicQueryRewriteShortcuts.tryRewrite(normalized.normalizedText());
        if (deterministic.isPresent()) {
            return validateOrFallback(deterministic.get(), normalized, entities);
        }

        RagLlmCallBudgetPolicy.SkipDecision rewriteSkip =
                RagLlmCallBudgetPolicy.llmRewriteDecision(ctx, normalized.normalizedText());
        if (rewriteSkip.skip()) {
            return new StructuredRewriteResult(
                    normalized.normalizedText(),
                    false,
                    List.of("OK: rewrite_skipped:" + rewriteSkip.reason()),
                    StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    Map.of(),
                    List.of());
        }

        try {
            String prompt = buildPrompt(ctx, normalized, classifierLabel, classifierQueryType, classifierStatus, entities);
            String response = invokeRewriteModel(ctx, prompt);
            StructuredRewriteResult parsed = parse(response, normalized.normalizedText());
            return validateOrFallback(parsed, normalized, entities);
        } catch (Exception e) {
            return StructuredRewriteResult.identityFallback(normalized.normalizedText(), safeMsg(e));
        }
    }

    private String invokeRewriteModel(ExecutionContext ctx, String userPrompt) {
        String system =
                promptResolver.resolveSystem(ConfigurablePromptGroup.QUERY_REWRITE, ctx.userId(), ctx.projectId());
        return secondaryLlmExecutor.complete(
                ctx,
                "query-rewrite",
                system,
                userPrompt,
                ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE);
    }

    private String buildPrompt(
            ExecutionContext ctx,
            NormalizedQuery normalized,
            String classifierLabel,
            Optional<QueryType> classifierQueryType,
            ClassifierStatus classifierStatus,
            EntityExtractionResult entities) {

        Optional<QueryType> cqt = Objects.requireNonNullElseGet(classifierQueryType, Optional::empty);
        String dates = String.join(", ", entities.dates());
        String people = String.join(", ", entities.people());
        String locations = String.join(", ", entities.locations());
        String topics = String.join(", ", entities.topics());
        String orgs = String.join(", ", entities.organizations());

        String template =
                promptResolver.resolve(ConfigurablePromptGroup.QUERY_REWRITE, ctx.userId(), ctx.projectId());
        return template.formatted(
                escape(normalized.normalizedText()),
                classifierStatus,
                escape(classifierLabel),
                cqt.isPresent() ? cqt.get().name() : "null",
                escape(dates),
                escape(people),
                escape(locations),
                escape(topics),
                escape(orgs)
        );
    }

    private static StructuredRewriteResult parse(String response, String normalizedText) throws Exception {
        String json = extractJsonObject(response);
        JsonNode node = MAPPER.readTree(json);

        String rewritten =
                text(node, "rewrittenQueryText")
                        .orElseThrow(() -> new IllegalArgumentException("missing rewrittenQueryText"));
        List<String> targetEntities = list(node, "targetEntities");
        List<String> targetAttributes = list(node, "targetAttributes");
        Optional<String> targetAction = text(node, "targetAction").filter(s -> !s.equalsIgnoreCase("null"));
        Map<String, String> slotFilling = map(node, "slotFilling");
        List<String> constraints = list(node, "constraints");

        boolean applied = !rewritten.isBlank() && !rewritten.equals(normalizedText);
        List<String> notes = List.of("OK");

        if (rewritten.isBlank()) {
            throw new IllegalArgumentException("blank rewrittenQueryText");
        }

        return new StructuredRewriteResult(
                rewritten,
                applied,
                notes,
                StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                targetEntities,
                targetAttributes,
                targetAction,
                slotFilling,
                constraints
        );
    }

    private static StructuredRewriteResult validateOrFallback(
            StructuredRewriteResult candidate,
            NormalizedQuery normalized,
            EntityExtractionResult entities) {

        String normalizedText = normalized.normalizedText();
        String rewritten = candidate.rewrittenQueryText();

        if (rewritten.isBlank()) {
            return StructuredRewriteResult.identityFallback(normalizedText, "blank_rewrite");
        }

        int n = normalizedText.length();
        int maxA = (int) Math.ceil(1.5 * n);
        int maxB = n + 300;
        int max = Math.min(maxA, maxB);
        if (rewritten.length() > max) {
            return StructuredRewriteResult.identityFallback(normalizedText, "length_violation");
        }

        String rewrittenLc = rewritten.toLowerCase(Locale.ROOT);
        for (String d : entities.dates()) {
            if (d == null || d.isBlank()) continue;
            if (!rewrittenLc.contains(d.toLowerCase(Locale.ROOT))) {
                return StructuredRewriteResult.identityFallback(normalizedText, "dropped_date:" + d);
            }
        }

        for (String e : requiredEntities(entities)) {
            if (!rewrittenLc.contains(e.toLowerCase(Locale.ROOT))) {
                return StructuredRewriteResult.identityFallback(normalizedText, "dropped_entity:" + e);
            }
        }

        if (carriedFieldRewriteBlocked(normalizedText, rewrittenLc)) {
            return StructuredRewriteResult.identityFallback(normalizedText, "carried_field_frozen");
        }

        // Validate that structured targetEntities do not introduce entities not present in extracted entities or query text.
        Set<String> allowed = allowedEntities(normalizedText, entities);
        for (String te : candidate.targetEntities()) {
            if (te == null || te.isBlank()) continue;
            if (!allowed.contains(te.trim().toLowerCase(Locale.ROOT))) {
                return StructuredRewriteResult.identityFallback(normalizedText, "introduced_target_entity:" + te);
            }
        }

        return candidate;
    }

    private static boolean carriedFieldRewriteBlocked(String normalizedText, String rewrittenLc) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        String original = normalizedText.toLowerCase(Locale.ROOT);
        boolean carriedAttendees =
                (original.contains("asistentes") || original.contains("participantes"))
                        && (original.contains("cuántos") || original.contains("cuantos"));
        if (!carriedAttendees) {
            return false;
        }
        boolean rewriteToDuration =
                rewrittenLc.contains("duración")
                        || rewrittenLc.contains("duracion")
                        || rewrittenLc.contains("cuánto duró")
                        || rewrittenLc.contains("cuanto duro");
        return rewriteToDuration;
    }

    private static List<String> requiredEntities(EntityExtractionResult entities) {
        List<String> out = new ArrayList<>();
        out.addAll(entities.people());
        out.addAll(entities.locations());
        out.addAll(entities.organizations());
        return out.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    private static Set<String> allowedEntities(String normalizedText, EntityExtractionResult entities) {
        List<String> allowed = new ArrayList<>();
        allowed.addAll(requiredEntities(entities));
        allowed.addAll(entities.topics());
        allowed.addAll(entities.dates());
        // Also allow exact substrings already present in the normalized text (conservative)
        allowed.addAll(splitTokens(normalizedText));
        return allowed.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> splitTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\s+"));
    }

    private static Optional<String> text(JsonNode node, String key) {
        if (node == null || key == null) return Optional.empty();
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) return Optional.empty();
        if (!v.isTextual()) return Optional.empty();
        String s = v.asText();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s.trim());
    }

    private static List<String> list(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull() || !v.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode it : v) {
            if (it != null && it.isTextual()) {
                String s = it.asText();
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out.stream().distinct().toList();
    }

    private static Map<String, String> map(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull() || !v.isObject()) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        v.properties().forEach(e -> {
            if (e.getKey() == null || e.getKey().isBlank()) return;
            JsonNode val = e.getValue();
            if (val != null && val.isTextual()) {
                String s = val.asText();
                if (s != null && !s.isBlank()) {
                    out.put(e.getKey().trim(), s.trim());
                }
            }
        });
        return Map.copyOf(out);
    }

    private static String extractJsonObject(String response) {
        if (response == null) {
            return "{}";
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "{}";
        }
        return response.substring(start, end + 1);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}

