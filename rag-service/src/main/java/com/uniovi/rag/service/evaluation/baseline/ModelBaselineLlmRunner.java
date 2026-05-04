package com.uniovi.rag.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes Phase 5 LLM baseline protocols via {@link ChatClient} + snapshot-bound {@link OllamaOptions}. */
@Component
public class ModelBaselineLlmRunner {

    private final ChatClient chatClient;

    public ModelBaselineLlmRunner(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Build user turn text (no system prompt here — caller passes composed effective system).
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
        OllamaOptions options = BaselineOllamaOptionsMapper.toOllamaOptions(snapshot);
        String user =
                switch (protocol) {
                    case LLM_READER_ORACLE_CONTEXT -> buildOracleUserTurn(q, oracleContext);
                    case LLM_FULL_DOCUMENT_CONTEXT -> buildFullDocUserTurn(q, fullDocumentText, truncation);
                    default ->
                            throw new IllegalArgumentException("Unsupported LLM baseline protocol: " + protocol);
                };
        String system = prompts.effectiveSystemPrompt() != null ? prompts.effectiveSystemPrompt() : "";
        return chatClient
                .prompt()
                .system(system)
                .user(user)
                .options(options)
                .call()
                .content();
    }

    /**
     * Fixed-LLM answer step for {@link com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol#EMBEDDING_DOWNSTREAM_RAG}
     * (retrieval already executed by caller).
     */
    public String generateAnswerFromRetrievedChunks(
            LlmExperimentalSnapshot snapshot, PromptProfileSnapshot prompts, String query, List<Document> retrievedChunks) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(prompts, "prompts");
        OllamaOptions options = BaselineOllamaOptionsMapper.toOllamaOptions(snapshot);
        String user = DownstreamRagAnswerPrompt.userTurn(query, retrievedChunks, prompts);
        String system = prompts.effectiveSystemPrompt() != null ? prompts.effectiveSystemPrompt() : "";
        return chatClient.prompt().system(system).user(user).options(options).call().content();
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
                DOCUMENT CONTEXT (possibly truncated — see TRUNCATION_NOTE):
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
