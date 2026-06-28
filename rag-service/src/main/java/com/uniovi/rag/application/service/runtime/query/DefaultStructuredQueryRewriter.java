package com.uniovi.rag.application.service.runtime.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

@Service
public class DefaultStructuredQueryRewriter implements StructuredQueryRewriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ChatGenerationModelSelector chatGenerationModelSelector;

    public DefaultStructuredQueryRewriter(
            ChatClient chatClient, ChatGenerationModelSelector chatGenerationModelSelector) {
        this.chatClient = chatClient;
        this.chatGenerationModelSelector = chatGenerationModelSelector;
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
        if (!rag.toolsEnabled()) {
            return StructuredRewriteResult.identityDisabled(normalized.normalizedText(), "toolsEnabled=false");
        }

        try {
            String prompt = buildPrompt(normalized, classifierLabel, classifierQueryType, classifierStatus, entities);
            String response = invokeRewriteModel(ctx, prompt);
            StructuredRewriteResult parsed = parse(response, normalized.normalizedText());
            return validateOrFallback(parsed, normalized, entities);
        } catch (Exception e) {
            return StructuredRewriteResult.identityFallback(normalized.normalizedText(), safeMsg(e));
        }
    }

    private String invokeRewriteModel(ExecutionContext ctx, String userPrompt) {
        var spec = chatClient.prompt()
                .system("""
                        You are a deterministic query rewriter.
                        Return ONLY a JSON object. No markdown. No extra text.
                        The response must start with { and end with }.
                        """)
                .user(userPrompt);

        // Fixed low-temperature to reduce variance (when supported by the client/model).
        OllamaOptions.Builder opt = OllamaOptions.builder().temperature(0.0);
        chatGenerationModelSelector.effectiveChatModelId(ctx).ifPresent(opt::model);
        spec = spec.options(opt.build());
        String out = spec.call().content();
        return out == null ? "" : out.trim();
    }

    private static String buildPrompt(
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

        return """
                Rewrite the query in a constrained way.

                INPUTS:
                - normalizedText: "%s"
                - classifierStatus: "%s"
                - classifierLabel: "%s"
                - classifierQueryType: "%s"
                - extractedEntities:
                  - dates: [%s]
                  - people: [%s]
                  - locations: [%s]
                  - topics: [%s]
                  - organizations: [%s]

                OUTPUT JSON SCHEMA (all keys required; use empty lists/maps when absent):
                {
                  "rewrittenQueryText": "string",
                  "targetEntities": ["string"],
                  "targetAttributes": ["string"],
                  "targetAction": "COUNT|LIST|FIND|EXPLAIN|SUMMARIZE|COMPARE|EXTRACT_FIELD|BOOLEAN_CHECK|UNKNOWN|null",
                  "slotFilling": {"key":"value"},
                  "constraints": ["string"]
                }

                CONSTRAINTS:
                - rewrittenQueryText MUST preserve any temporal constraints and must not drop named entities present in inputs
                - rewrittenQueryText MUST NOT introduce new named entities not present in inputs
                - rewrittenQueryText MUST NOT exceed 1.5x input length and MUST NOT exceed input length + 300
                - Do not invent missing constraints. If uncertain, leave fields empty and keep rewrittenQueryText close to input.
                """.formatted(
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

