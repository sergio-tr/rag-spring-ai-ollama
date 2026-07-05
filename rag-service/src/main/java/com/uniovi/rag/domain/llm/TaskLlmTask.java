package com.uniovi.rag.domain.llm;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Secondary LLM tasks with optional per-task model overrides. */
public enum TaskLlmTask {
    FINAL_ANSWER("final_answer", "Final answer", true),
    QUERY_REWRITE("query_rewrite", "Query rewrite", false),
    QUERY_EXPANSION("query_expansion", "Query expansion", false),
    MEMORY_CONDENSE("memory_condense", "Memory condensation", false),
    RUNTIME_JUDGE("runtime_judge", "Runtime answer judge", false),
    RUNTIME_JUDGE_RETRY("runtime_judge_retry", "Runtime judge retry", false),
    FACTUAL_VERIFIER("factual_verifier", "Factual verifier", false),
    LLM_RANKER("llm_ranker", "LLM ranker", false),
    METADATA_REASONING("metadata_reasoning", "Metadata reasoning", false),
    NER_EXTRACTION("ner_extraction", "NER extraction", false),
    EVALUATION_JUDGE("evaluation_judge", "Evaluation judge", true),
    LLM_BASELINE_EVALUATION("llm_baseline_evaluation", "LLM baseline evaluation", true);

    private final String id;
    private final String label;
    private final boolean inheritsMainModelByDefault;

    TaskLlmTask(String id, String label, boolean inheritsMainModelByDefault) {
        this.id = id;
        this.label = label;
        this.inheritsMainModelByDefault = inheritsMainModelByDefault;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public boolean inheritsMainModelByDefault() {
        return inheritsMainModelByDefault;
    }

    public String operationName() {
        return switch (this) {
            case FINAL_ANSWER -> "final-answer";
            case QUERY_REWRITE -> "query-rewrite";
            case QUERY_EXPANSION -> "query-expansion";
            case MEMORY_CONDENSE -> "conversation-condense";
            case RUNTIME_JUDGE, RUNTIME_JUDGE_RETRY -> "runtime-judge";
            case FACTUAL_VERIFIER -> "factual-revision";
            case LLM_RANKER -> "llm-ranker";
            case METADATA_REASONING -> "metadata-reasoning";
            case NER_EXTRACTION -> "ner-extraction";
            case EVALUATION_JUDGE -> "evaluation-judge";
            case LLM_BASELINE_EVALUATION -> "llm-baseline-evaluation";
        };
    }

    public static TaskLlmTask fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("task id required");
        }
        String normalized = raw.trim();
        for (TaskLlmTask task : values()) {
            if (task.id.equals(normalized)) {
                return task;
            }
        }
        throw new IllegalArgumentException("Unknown task LLM id: " + raw);
    }

    public static List<TaskLlmTask> catalogTasks() {
        return List.of(values());
    }

    /** Maps runtime secondary LLM operation ids to configurable task overrides. */
    public static Optional<TaskLlmTask> fromOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return Optional.empty();
        }
        String op = operation.trim().toLowerCase(Locale.ROOT);
        return switch (op) {
            case "query-rewrite" -> Optional.of(QUERY_REWRITE);
            case "query-expansion" -> Optional.of(QUERY_EXPANSION);
            case "conversation-condense" -> Optional.of(MEMORY_CONDENSE);
            case "runtime-judge" -> Optional.of(RUNTIME_JUDGE);
            case "runtime-judge-retry" -> Optional.of(RUNTIME_JUDGE_RETRY);
            case "llm-ranker" -> Optional.of(LLM_RANKER);
            case "factual-revision", "answer-quality-check", "structured-answer-plan" -> Optional.of(FACTUAL_VERIFIER);
            case "reasoning-cot-pre", "reasoning-cot-post", "reasoning-plan-pre", "reasoning-plan-post" ->
                    Optional.of(FACTUAL_VERIFIER);
            case "function-calling" -> Optional.of(FINAL_ANSWER);
            case "llm-baseline-evaluation" -> Optional.of(LLM_BASELINE_EVALUATION);
            case "evaluation-judge" -> Optional.of(EVALUATION_JUDGE);
            case "ner", "ner-extraction" -> Optional.of(NER_EXTRACTION);
            default -> op.startsWith("metadata-") ? Optional.of(METADATA_REASONING) : Optional.empty();
        };
    }
}
