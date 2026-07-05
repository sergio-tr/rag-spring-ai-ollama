package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/** Executes Phase 5 LLM baseline protocols via {@link LlmClientResolver} and snapshot-bound config. */
@Component
public class ModelBaselineLlmRunner {

    public static final String OPERATION_LLM_BASELINE_EVALUATION = "llm-baseline-evaluation";

    private static final Logger log = LoggerFactory.getLogger(ModelBaselineLlmRunner.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver configResolver;
    private final EvaluationRunRepository evaluationRunRepository;

    public ModelBaselineLlmRunner(
            LlmClientResolver llmClientResolver,
            ResolvedLlmConfigResolver configResolver,
            EvaluationRunRepository evaluationRunRepository) {
        this.llmClientResolver = llmClientResolver;
        this.configResolver = configResolver;
        this.evaluationRunRepository = evaluationRunRepository;
    }

    /**
     * Build user turn text (no system prompt here - caller passes composed effective system).
     */
    public String generateAnswer(
            BenchmarkEvaluationProtocol protocol,
            LlmExperimentalSnapshot snapshot,
            PromptProfileSnapshot prompts,
            String question,
            String oracleContext,
            String fullDocumentText,
            DocumentContextTruncator.Result truncation) {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(prompts, "prompts");
        String q = question != null ? question : "";
        String user =
                switch (protocol) {
                    case LLM_READER_ORACLE_CONTEXT -> buildOracleUserTurn(q, oracleContext);
                    case LLM_FULL_DOCUMENT_CONTEXT -> buildFullDocUserTurn(q, fullDocumentText, truncation);
                    default ->
                            throw new IllegalArgumentException("Unsupported LLM baseline protocol: " + protocol);
                };
        String system = prompts.effectiveSystemPrompt() != null ? prompts.effectiveSystemPrompt() : "";
        return complete(snapshot, system, user);
    }

    /**
     * Fixed-LLM answer step for {@link com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol#EMBEDDING_DOWNSTREAM_RAG}
     * (retrieval already executed by caller).
     */
    public String generateAnswerFromRetrievedChunks(
            LlmExperimentalSnapshot snapshot, PromptProfileSnapshot prompts, String query, List<Document> retrievedChunks) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(prompts, "prompts");
        String user = DownstreamRagAnswerPrompt.userTurn(query, retrievedChunks, prompts);
        String system = prompts.effectiveSystemPrompt() != null ? prompts.effectiveSystemPrompt() : "";
        return complete(snapshot, system, user);
    }

    private String complete(LlmExperimentalSnapshot snapshot, String system, String user) {
        ResolvedLlmConfig base = resolveBaseConfig();
        ResolvedLlmConfig config = LlmExperimentalSnapshotConfigMapper.toResolvedConfig(base, snapshot);
        String model = snapshot.model();
        log.debug(
                "Secondary LLM call: operation={} provider={} model={} baseUrl={} temperature={}",
                OPERATION_LLM_BASELINE_EVALUATION,
                config.chatProvider(),
                model,
                config.baseUrl(),
                config.temperature());
        LlmChatClient client = llmClientResolver.resolveChatClient(config);
        LlmChatResponse response =
                client.chat(
                        LlmChatRequest.of(
                                model,
                                system,
                                user,
                                config.temperature(),
                                config.timeoutMs(),
                                config.additionalParameters()));
        String content = response != null ? response.content() : null;
        if (content == null || content.isBlank()) {
            throw new IllegalStateException(
                    "Empty LLM baseline response for provider=" + config.chatProvider() + " model=" + model);
        }
        return content.trim();
    }

    private ResolvedLlmConfig resolveBaseConfig() {
        UUID userId = resolveUserId();
        UUID projectId = resolveProjectId();
        return configResolver.resolve(userId, projectId, null);
    }

    private UUID resolveUserId() {
        return LabBenchmarkExecutionContext.currentLabRuntimeContext()
                .flatMap(ctx -> ctx.runId() != null ? evaluationRunRepository.findById(ctx.runId()) : Optional.empty())
                .map(EvaluationRunEntity::getUser)
                .map(u -> u.getId())
                .orElse(null);
    }

    private UUID resolveProjectId() {
        return LabBenchmarkExecutionContext.currentLabRuntimeContext()
                .flatMap(ctx -> ctx.runId() != null ? evaluationRunRepository.findById(ctx.runId()) : Optional.empty())
                .map(EvaluationRunEntity::getProject)
                .map(p -> p.getId())
                .orElse(null);
    }

    private static String buildOracleUserTurn(String question, String context) {
        String ctx = context != null ? context.trim() : "";
        if (ctx.isEmpty()) {
            return "Answer using only general reasoning (no embedded context row was provided).\n\nQuestion:\n" + question;
        }
        return """
                CONTEXT:
                %s

                QUESTION:
                %s
                """
                .formatted(ctx, question);
    }

    private static String buildFullDocUserTurn(String question, String documentText, DocumentContextTruncator.Result truncation) {
        String doc = documentText != null ? documentText : "";
        String applied =
                truncation != null && truncation.textUsed() != null ? truncation.textUsed() : doc;
        String note =
                truncation != null && truncation.note() != null
                        ? truncation.note()
                        : "No truncation metadata.";
        return """
                DOCUMENT CONTEXT (possibly truncated - see TRUNCATION_NOTE):
                %s

                TRUNCATION_NOTE:
                %s

                QUESTION:
                %s
                """
                .formatted(applied, note, question);
    }

    /** Useful for metrics attachment on result rows. */
    public static Map<String, Object> truncationMetrics(DocumentContextTruncator.Result r) {
        if (r == null) {
            return Map.of();
        }
        return Map.of(
                "truncated", r.truncated(),
                "original_document_chars", r.originalChars(),
                "applied_char_budget", r.maxCharsApplied(),
                "truncation_strategy_note", r.note());
    }
}
