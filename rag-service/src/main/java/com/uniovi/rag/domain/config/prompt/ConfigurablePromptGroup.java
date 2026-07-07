package com.uniovi.rag.domain.config.prompt;

import com.uniovi.rag.application.service.evaluation.EvaluationJudgePromptSources;
import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import com.uniovi.rag.application.service.runtime.factual.FactualRevisionPrompts;
import com.uniovi.rag.application.service.runtime.judge.RuntimeJudgePromptSources;
import com.uniovi.rag.application.service.runtime.memory.ConversationCondensePromptSources;
import com.uniovi.rag.application.service.runtime.query.QueryRewritePromptSources;
import com.uniovi.rag.application.service.runtime.query.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.application.service.runtime.ranking.LLMAsJudgeRanker;
import com.uniovi.rag.application.service.runtime.query.analyser.MinuteNERQueryAnalyser;
import java.util.List;

/**
 * User-configurable internal prompt groups exposed via {@code GET /config/prompt-catalog}.
 * Overrides are stored under {@code promptOverrides.<id>} in {@code rag_configuration.values}.
 */
public enum ConfigurablePromptGroup {
    SYSTEM_INSTRUCTIONS(
            "system_instructions",
            "System instructions",
            "Primary assistant system prompt merged into chat generation.",
            List.of(),
            List.of(),
            true,
            true),
    PROJECT_CONTEXT(
            "project_context",
            "Project context",
            "Project-scoped instructions merged into the system prompt (Project settings → Assistant instructions).",
            List.of(),
            List.of(),
            false,
            false),
    ANSWER_SYNTHESIS(
            "answer_synthesis",
            "Answer synthesis",
            "User-turn template for document-grounded answer generation.",
            List.of("%s", "<Question>", "<Context>"),
            List.of(),
            false,
            true),
    ABSTENTION(
            "abstention",
            "Abstention message",
            "Message shown when document context is insufficient.",
            List.of(),
            List.of(),
            false,
            true),
    SOURCE_GROUNDING(
            "source_grounding",
            "Source grounding policy",
            "Grounding policy block embedded in answer synthesis templates.",
            List.of("%s"),
            List.of("<Question>", "<Context>"),
            false,
            true),
    QUERY_REWRITE(
            "query_rewrite",
            "Query rewrite",
            "Structured query rewrite user template.",
            List.of("%s"),
            List.of(),
            false,
            true),
    QUERY_EXPANSION(
            "query_expansion",
            "Query expansion",
            "Meeting-minutes query rephrase/expansion prompt.",
            List.of("%s"),
            List.of(),
            false,
            true),
    MEMORY_CONDENSE(
            "memory_condense",
            "Memory condensation",
            "Multi-turn conversation query condensation wrapper.",
            List.of("%s"),
            List.of(),
            false,
            true),
    RUNTIME_JUDGE(
            "runtime_judge",
            "Runtime answer judge",
            "Post-answer quality judge template.",
            List.of("%s"),
            List.of(),
            false,
            true),
    RUNTIME_JUDGE_RETRY(
            "runtime_judge_retry",
            "Runtime judge retry policy",
            "Retry-allowed vs retry-denied policy lines for the runtime judge.",
            List.of("RETRY_REQUESTED", "REJECTED_NO_RETRY"),
            List.of(),
            false,
            true),
    FACTUAL_VERIFIER(
            "factual_verifier",
            "Factual verifier revision",
            "Factual revision user-turn template after verifier failures.",
            List.of("%s", "<Question>", "<Context>", "<DraftAnswer>"),
            List.of(),
            false,
            true),
    LLM_RANKER(
            "llm_ranker",
            "LLM ranker",
            "Candidate response ranking judge template.",
            List.of("%s"),
            List.of(),
            false,
            true),
    METADATA_FILTER_AND_LIST(
            "metadata_filter_and_list",
            "Metadata filter and list",
            "Semantic metadata filter for list-style queries.",
            List.of("%s"),
            List.of(),
            false,
            true),
    METADATA_BOOLEAN_QUERY(
            "metadata_boolean_query",
            "Metadata boolean query",
            "Boolean metadata matching prompt.",
            List.of("%s"),
            List.of(),
            false,
            true),
    METADATA_GET_FIELD(
            "metadata_get_field",
            "Metadata field extraction",
            "Field-level metadata extraction prompt.",
            List.of("%s"),
            List.of(),
            false,
            true),
    METADATA_SUMMARIZE_MEETING(
            "metadata_summarize_meeting",
            "Metadata meeting summary",
            "Meeting summary generation system prompt.",
            List.of(),
            List.of(),
            false,
            true),
    EVALUATION_JUDGE(
            "evaluation_judge",
            "Evaluation judge",
            "Lab evaluation judge scoring template.",
            List.of("{question}", "{correctAnswer}", "{generatedAnswer}"),
            List.of(),
            false,
            true),
    NER_EXTRACTION(
            "ner_extraction",
            "NER extraction",
            "Named-entity extraction prompt for meeting-minutes query analysis.",
            List.of("{query}"),
            List.of(),
            false,
            true),
    FUNCTION_CALLING_USER_ASSEMBLY(
            "function_calling_user_assembly",
            "Function calling user assembly",
            "Programmatic user-turn assembly for tool-enabled rounds (query plan + structured context). "
                    + "Not user-editable; listed for catalog alignment only.",
            List.of(),
            List.of(),
            false,
            false);

    private final String id;
    private final String componentLabel;
    private final String description;
    private final List<String> requiredVariables;
    private final List<String> optionalVariables;
    private final boolean usesLlmSystemPromptKey;
    private final boolean runtimeEditable;

    ConfigurablePromptGroup(
            String id,
            String componentLabel,
            String description,
            List<String> requiredVariables,
            List<String> optionalVariables,
            boolean usesLlmSystemPromptKey) {
        this(id, componentLabel, description, requiredVariables, optionalVariables, usesLlmSystemPromptKey, true);
    }

    ConfigurablePromptGroup(
            String id,
            String componentLabel,
            String description,
            List<String> requiredVariables,
            List<String> optionalVariables,
            boolean usesLlmSystemPromptKey,
            boolean runtimeEditable) {
        this.id = id;
        this.componentLabel = componentLabel;
        this.description = description;
        this.requiredVariables = List.copyOf(requiredVariables);
        this.optionalVariables = List.copyOf(optionalVariables);
        this.usesLlmSystemPromptKey = usesLlmSystemPromptKey;
        this.runtimeEditable = runtimeEditable;
    }

    public String id() {
        return id;
    }

    public String componentLabel() {
        return componentLabel;
    }

    public String description() {
        return description;
    }

    public List<String> requiredVariables() {
        return requiredVariables;
    }

    public List<String> optionalVariables() {
        return optionalVariables;
    }

    public boolean usesLlmSystemPromptKey() {
        return usesLlmSystemPromptKey;
    }

    /** When false, prompt is catalog/documentation only - runtime does not apply overrides yet. */
    public boolean runtimeEditable() {
        return runtimeEditable;
    }

    public String storageKey() {
        return usesLlmSystemPromptKey ? "llmSystemPrompt" : PromptOverrideKeys.overrideKey(id);
    }

    public String defaultContent() {
        return switch (this) {
            case SYSTEM_INSTRUCTIONS, PROJECT_CONTEXT -> "";
            case ANSWER_SYNTHESIS -> RuntimeAnswerPrompts.defaultAnswerSynthesisTemplate();
            case ABSTENTION -> RuntimeAnswerPrompts.defaultAbstentionMessageEn();
            case SOURCE_GROUNDING -> RuntimeAnswerPrompts.defaultSourceGroundingBlock();
            case QUERY_REWRITE -> QueryRewritePromptSources.defaultUserTemplate();
            case QUERY_EXPANSION -> MinuteDocumentStructureExpander.defaultExpansionPrompt();
            case MEMORY_CONDENSE -> ConversationCondensePromptSources.defaultUserWrapper();
            case RUNTIME_JUDGE -> RuntimeJudgePromptSources.defaultTemplate();
            case RUNTIME_JUDGE_RETRY -> RuntimeJudgePromptSources.defaultRetryPolicyMaterial();
            case FACTUAL_VERIFIER -> FactualRevisionPrompts.defaultRevisionTemplate();
            case LLM_RANKER -> LLMAsJudgeRanker.defaultPromptTemplate();
            case METADATA_FILTER_AND_LIST -> MetadataConfigurablePromptSources.FILTER_AND_LIST;
            case METADATA_BOOLEAN_QUERY -> MetadataConfigurablePromptSources.BOOLEAN_QUERY;
            case METADATA_GET_FIELD -> MetadataConfigurablePromptSources.GET_FIELD;
            case METADATA_SUMMARIZE_MEETING -> MetadataMinuteDocumentService.SYSTEM_PROMPT_SUMMARY;
            case EVALUATION_JUDGE -> EvaluationJudgePromptSources.defaultTemplate();
            case NER_EXTRACTION -> MinuteNERQueryAnalyser.defaultPromptTemplate();
            case FUNCTION_CALLING_USER_ASSEMBLY ->
                    "Assembled at runtime from the query plan (see FunctionCallingPrompts).";
        };
    }

    public String defaultSystemContent() {
        return switch (this) {
            case QUERY_REWRITE -> QueryRewritePromptSources.defaultSystemPrompt();
            case MEMORY_CONDENSE -> ConversationCondensePromptSources.defaultSystemPrompt();
            default -> "";
        };
    }

    public static ConfigurablePromptGroup fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("prompt group id required");
        }
        String normalized = raw.trim();
        for (ConfigurablePromptGroup g : values()) {
            if (g.id.equals(normalized)) {
                return g;
            }
        }
        throw new IllegalArgumentException("Unknown prompt group: " + raw);
    }
}
