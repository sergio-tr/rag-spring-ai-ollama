package com.uniovi.rag.application.service.runtime.reasoning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * R8A: generates a safe structured answer plan (no chain-of-thought).
 *
 * <p>The plan is used only to guide controlled generation; it must never be shown to end users.</p>
 */
@Service
public class StructuredAnswerPlanService {

    public static final String OPERATION_STRUCTURED_ANSWER_PLAN = "structured-answer-plan";

    private static final int MAX_MODEL_OUTPUT_CHARS = 2000;
    private static final int MAX_SAFE_SUMMARY_CHARS = 220;

    private static final String PROMPT = """
            You are generating an internal, SAFE answer plan for a RAG system.
            IMPORTANT: Do NOT reveal chain-of-thought. Do NOT include hidden reasoning.
            Provide only structured, user-safe guidance.

            Return STRICT JSON with this shape:
            {
              "strategy": "SAFE_STRUCTURED_PLAN",
              "objective": "string",
              "expectedEvidence": ["string", ...],
              "answerConstraints": ["string", ...],
              "verificationChecklist": ["string", ...],
              "safeSummary": "string"
            }

            Guidelines:
            - objective: what the response should achieve (one sentence).
            - expectedEvidence: what document evidence is required (e.g. exact date, acta title, quoted excerpt).
            - answerConstraints: safety / grounding constraints (e.g. use only context; mention mismatch if missing).
            - verificationChecklist: quick checks before finalizing.
            - safeSummary: <= 200 chars summary of the plan intent.

            Question: %s
            QueryType: %s
            """;

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private final ObjectMapper objectMapper;

    public StructuredAnswerPlanService(
            ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor, ObjectMapper objectMapper) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
        this.objectMapper = objectMapper;
    }

    public PlanResult plan(ExecutionContext ctx, QueryPlan plan) {
        long t0 = System.nanoTime();
        try {
            String q = plan != null ? plan.rewrittenQueryText() : null;
            String queryText = q != null ? q : "";
            QueryType type = plan != null ? plan.classifierQueryType().orElse(null) : null;
            String prompt = String.format(PROMPT, queryText, type != null ? type.name() : "UNKNOWN");

            String raw =
                    secondaryLlmExecutor.complete(
                            ctx,
                            OPERATION_STRUCTURED_ANSWER_PLAN,
                            null,
                            prompt,
                            ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE);
            ResolvedLlmConfig config = secondaryLlmExecutor.effectiveConfig(ctx);
            String trimmed = safe(raw, MAX_MODEL_OUTPUT_CHARS);
            StructuredAnswerPlan out = parse(trimmed);
            ExecutionStageTrace trace =
                    new ExecutionStageTrace(
                            "reasoning_plan",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                            ExecutionStageOutcome.SUCCESS,
                            "strategy="
                                    + out.strategy()
                                    + " summary="
                                    + safe(out.safeSummary(), MAX_SAFE_SUMMARY_CHARS)
                                    + " operation="
                                    + OPERATION_STRUCTURED_ANSWER_PLAN
                                    + " provider="
                                    + config.chatProvider()
                                    + " model="
                                    + config.chatModel());
            return new PlanResult(Optional.of(out), List.of(trace));
        } catch (Exception e) {
            ExecutionStageTrace trace =
                    new ExecutionStageTrace(
                            "reasoning_plan",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                            ExecutionStageOutcome.FAILED,
                            "error=" + e.getClass().getSimpleName());
            return new PlanResult(Optional.empty(), List.of(trace));
        }
    }

    private StructuredAnswerPlan parse(String json) {
        if (json == null || json.isBlank()) {
            return new StructuredAnswerPlan(
                    "SAFE_STRUCTURED_PLAN",
                    "",
                    List.of(),
                    List.of("Use only the provided context for document-specific claims."),
                    List.of("If exact evidence is missing, explain what is missing."),
                    "");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String objective = text(root, "objective");
            String safeSummary = safe(text(root, "safeSummary"), MAX_SAFE_SUMMARY_CHARS);
            List<String> expectedEvidence = list(root, "expectedEvidence");
            List<String> constraints = list(root, "answerConstraints");
            List<String> verify = list(root, "verificationChecklist");
            return new StructuredAnswerPlan(
                    "SAFE_STRUCTURED_PLAN",
                    objective,
                    expectedEvidence,
                    constraints,
                    verify,
                    safeSummary);
        } catch (Exception e) {
            return new StructuredAnswerPlan(
                    "SAFE_STRUCTURED_PLAN",
                    "",
                    List.of(),
                    List.of("Use only the provided context for document-specific claims."),
                    List.of("If exact evidence is missing, explain what is missing."),
                    "");
        }
    }

    private static String text(JsonNode root, String key) {
        if (root == null || key == null) {
            return "";
        }
        JsonNode n = root.get(key);
        return n != null && n.isTextual() ? n.asText() : "";
    }

    private static List<String> list(JsonNode root, String key) {
        if (root == null || key == null) {
            return List.of();
        }
        JsonNode n = root.get(key);
        if (n == null || !n.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode it : n) {
            if (it != null && it.isTextual()) {
                String s = it.asText();
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }

    private static String safe(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (maxChars <= 0 || t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars);
    }

    public record PlanResult(Optional<StructuredAnswerPlan> plan, List<ExecutionStageTrace> stageTraces) {}
}
