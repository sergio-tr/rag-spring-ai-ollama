package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.CandidateResponse;
import com.uniovi.rag.model.DraftAndContext;
import com.uniovi.rag.model.PostStepOutput;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.model.RankerResult;
import com.uniovi.rag.model.ReasoningPreOutput;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.pipeline.AnswerGenerationKernel;
import com.uniovi.rag.service.query.pipeline.ChatRequestSpecFactory;
import com.uniovi.rag.service.query.pipeline.CoreSynthesisResult;
import com.uniovi.rag.service.query.pipeline.PreparedQuery;
import com.uniovi.rag.service.query.pipeline.QueryInputPreparer;
import com.uniovi.rag.service.query.pipeline.ResponseSynthesisPipeline;
import com.uniovi.rag.service.query.pipeline.ToolRoutingService;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.api.ConnectivityFailureDetector;
import com.uniovi.rag.api.OllamaConnectivityChecker;
import com.uniovi.rag.exception.RagServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProcessQueryService implements QueryService {

    /** Ollama model per request (lab); the servlet thread allows a ThreadLocal-safe access. */
    private static final ThreadLocal<String> REQUEST_CHAT_MODEL = new ThreadLocal<>();

    private final RagFeatureConfiguration featureConfig;
    private final ChatClient chatClient;
    private final ReasoningStrategy reasoningStrategy;
    private final ResponseRanker responseRanker;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    private final ChatRequestSpecFactory chatRequestSpecFactory;
    private final QueryInputPreparer queryInputPreparer;
    private final ResponseSynthesisPipeline responseSynthesisPipeline;

    public ProcessQueryService(RagFeatureConfiguration featureConfig,
                               RagToolsConfiguration toolsConfig,
                               QueryExpander expander,
                               QueryAnalyser analyser,
                               NERQueryEnricher nerQueryEnricher,
                               QueryClassifier classifier,
                               ContextRetriever retriever,
                               ChatClient chatClient,
                               DateExistenceGuard dateExistenceGuard,
                               MeetingMinutesToolsAdapter meetingMinutesToolsAdapter,
                               ReasoningStrategy reasoningStrategy,
                               ResponseRanker responseRanker,
                               PostRetrievalProcessor postRetrievalProcessor,
                               ResponseValidator responseValidator,
                               QuestionAnswerAdvisor questionAnswerAdvisor,
                               OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.reasoningStrategy = reasoningStrategy;
        this.responseRanker = responseRanker;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;

        this.chatRequestSpecFactory = this::chatRequestSpec;
        this.queryInputPreparer = new QueryInputPreparer(featureConfig, expander, analyser, classifier);
        ToolRoutingService toolRouting = new ToolRoutingService(
                featureConfig, toolsConfig, meetingMinutesToolsAdapter, responseValidator, chatRequestSpecFactory);
        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig, nerQueryEnricher, retriever, postRetrievalProcessor, responseValidator, questionAnswerAdvisor, chatRequestSpecFactory);
        this.responseSynthesisPipeline = new ResponseSynthesisPipeline(
                featureConfig, dateExistenceGuard, toolRouting, kernel);
    }

    /**
     * Apply {@link OllamaOptions} with the chat model of this request when the lab sends an override.
     */
    private ChatClient.ChatClientRequestSpec chatRequestSpec() {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        String m = REQUEST_CHAT_MODEL.get();
        if (m != null && !m.isBlank()) {
            spec = spec.options(OllamaOptions.builder().model(m.trim()).build());
        }
        return spec;
    }

    @Override
    public QueryResponse generateResponse(String query, String chatModel) {
        try {
            if (chatModel != null && !chatModel.isBlank()) {
                REQUEST_CHAT_MODEL.set(chatModel.trim());
            }
            return generateResponseInternal(query);
        } finally {
            REQUEST_CHAT_MODEL.remove();
        }
    }

    private QueryResponse generateResponseInternal(String query) {

        try {
            if (query == null || query.trim().isEmpty()) {
                log().warn("Empty query received");
                String errorResponse = generateErrorResponse(query != null ? query : "", new IllegalArgumentException("empty query"));
                return QueryResponse.fromLLM(errorResponse);
            }

            ollamaConnectivityChecker.prepareForQuery(REQUEST_CHAT_MODEL.get());

            PreparedQuery pq = queryInputPreparer.prepare(query);

            log().info("Query expanded: {}", pq.expandedQuery());
            log().info("NER: {}", pq.nerEntities());
            log().info("Query Type : {}", pq.queryType());

            ReasoningPreOutput preOutput = null;
            if (featureConfig.isReasoningEnabled() && reasoningStrategy != null) {
                preOutput = reasoningStrategy.runPreStep(query, pq.queryType(), pq.nerEntities(), pq.expandedQuery());
            }

            CoreSynthesisResult core = responseSynthesisPipeline.synthesizeCore(pq, preOutput);

            if (featureConfig.isReasoningEnabled() && reasoningStrategy != null) {
                DraftAndContext dac = core.draftAndContext();
                if (dac.draft() != null && !dac.draft().trim().isEmpty()) {
                    if (!isDraftAcceptable(dac)) {
                        log().info("Draft not acceptable (A.5 fallback), using plain LLM");
                        String answer = responseSynthesisPipeline.fallbackPlainLlm(pq);
                        return QueryResponse.fromLLM(answer != null ? answer : dac.draft(), pq.queryType());
                    }
                    PostStepOutput postOutput = reasoningStrategy.runPostStep(query, dac.context(), dac.draft());
                    String candidate = (postOutput != null && postOutput.verifiedOrRefinedText() != null)
                            ? postOutput.verifiedOrRefinedText()
                            : dac.draft();
                    List<CandidateResponse> candidates = new ArrayList<>();
                    candidates.add(CandidateResponse.of(candidate, "reasoning"));
                    if (featureConfig.isRankerEnabled() && responseRanker != null) {
                        RankerResult rankerResult = responseRanker.selectBest(query, dac.context(), candidates);
                        if (rankerResult != null && rankerResult.chosenText() != null) {
                            return QueryResponse.fromTool(rankerResult.chosenText(), "reasoning+ranker", pq.queryType());
                        }
                    }
                    return QueryResponse.fromTool(candidate, "reasoning", pq.queryType());
                }
            }

            return core.toDirectQueryResponse(pq.queryType());
        } catch (NullPointerException e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log().warn("Inference backend unreachable (NullPointerException chain): {}", e.getMessage());
                throw RagServiceException.llmUnavailable(e);
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                log().warn("Required Ollama model missing: {}", e.getMessage());
                throw RagServiceException.ollamaModelNotInstalled(e);
            }
            log().error("NullPointerException processing query (config: metadata={}, ner={}, tools={}): {}",
                    featureConfig.isMetadataEnabled(),
                    featureConfig.isNerEnabled(),
                    featureConfig.isToolsEnabled(),
                    query, e);
            log().error("Stack trace:", e);
            String errorResponse = generateErrorResponse(query, e);
            return QueryResponse.fromLLM(errorResponse);
        } catch (IllegalArgumentException e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log().warn("Inference backend unreachable (IllegalArgumentException chain): {}", e.getMessage());
                throw RagServiceException.llmUnavailable(e);
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                log().warn("Required Ollama model missing: {}", e.getMessage());
                throw RagServiceException.ollamaModelNotInstalled(e);
            }
            log().error("IllegalArgumentException processing query (config: metadata={}, ner={}, tools={}): {}",
                    featureConfig.isMetadataEnabled(),
                    featureConfig.isNerEnabled(),
                    featureConfig.isToolsEnabled(),
                    query, e);
            log().error("Stack trace:", e);
            String errorResponse = generateErrorResponse(query, e);
            return QueryResponse.fromLLM(errorResponse);
        } catch (Exception e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log().warn("Inference backend unreachable: {}", e.getMessage());
                throw RagServiceException.llmUnavailable(e);
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                log().warn("Required Ollama model missing: {}", e.getMessage());
                throw RagServiceException.ollamaModelNotInstalled(e);
            }
            log().error("Unexpected error processing query (config: metadata={}, ner={}, tools={}): {}",
                    featureConfig.isMetadataEnabled(),
                    featureConfig.isNerEnabled(),
                    featureConfig.isToolsEnabled(),
                    query, e);
            log().error("Exception type: {}, Message: {}", e.getClass().getName(), e.getMessage());
            log().error("Stack trace:", e);
            String errorResponse = generateErrorResponse(query, e);
            return QueryResponse.fromLLM(errorResponse);
        }
    }

    /**
     * Generates a user-facing apology when the pipeline fails for non-connectivity reasons.
     * Never calls the LLM when the failure is transport/connectivity (Ollama down) — that would recurse and spam logs.
     */
    private String generateErrorResponse(String query, Throwable cause) {
        if (cause != null && ConnectivityFailureDetector.isConnectivityFailure(cause)) {
            return "The AI inference service is unavailable. Please try again once Ollama is running and reachable.";
        }
        if (cause != null && ConnectivityFailureDetector.isOllamaModelMissingFailure(cause)) {
            return "A required Ollama model is not installed. Pull the chat and embedding models on the Ollama host "
                    + "(ollama pull …) or wait for automatic pull at startup.";
        }

        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            An error occurred while processing this query.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            apologizing for the error and asking the user to try again.
            Be concise and polite.
            Do not repeat the question.
            """, query != null ? query : "");

        try {
            String response = chatRequestSpec()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log().warn("Skipping LLM error message: inference backend unreachable");
                return "The AI inference service is unavailable. Please try again once Ollama is running and reachable.";
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                log().warn("Skipping LLM error message: Ollama model not installed");
                return "A required Ollama model is not installed. Pull the chat and embedding models on the Ollama host.";
            }
            log().warn("Error generating error response with LLM", e);
        }

        return "I'm sorry, an error occurred while processing your query. Please try again.";
    }

    /** A.5: Heuristic for unacceptable draft (empty, "not found" phrasing, too short). */
    private boolean isDraftAcceptable(DraftAndContext draftAndContext) {
        String draft = draftAndContext.draft();
        if (draft == null || draft.trim().isEmpty()) {
            return false;
        }
        if (draft.trim().length() < 30) {
            return false;
        }
        String lower = draft.toLowerCase();
        if (lower.contains("no puedo encontrar") || lower.contains("no encontrado") || lower.contains("no se encontró") || lower.contains("no hay información")) {
            return false;
        }
        return true;
    }
}
