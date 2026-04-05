package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.runtime.RagEffectiveFeatures;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.service.retriever.NaiveCorpusContextService;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.domain.config.EffectiveModelPolicy;
import com.uniovi.rag.application.model.CandidateResponse;
import com.uniovi.rag.application.model.DraftAndContext;
import com.uniovi.rag.application.model.PostStepOutput;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.RankerResult;
import com.uniovi.rag.application.model.ReasoningPreOutput;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.pipeline.ChatRequestSpecFactory;
import com.uniovi.rag.service.query.pipeline.CoreSynthesisResult;
import com.uniovi.rag.service.query.pipeline.PreparedQuery;
import com.uniovi.rag.service.query.pipeline.QueryInputPreparer;
import com.uniovi.rag.service.query.pipeline.ResponseSynthesisPipeline;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.interfaces.rest.support.ConnectivityFailureDetector;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.infrastructure.observability.TraceMdcBridge;
import io.micrometer.tracing.Tracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessQueryService implements QueryService {

    private static final String LOG_STACK_TRACE = "Stack trace:";

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
    private final ModelCatalogPort modelCatalogPort;
    private final ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    private final Tracer tracer;

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
                               OllamaConnectivityChecker ollamaConnectivityChecker,
                               NaiveCorpusContextService naiveCorpusContextService,
                               ModelCatalogPort modelCatalogPort,
                               ChatScopedRagConfigResolver chatScopedRagConfigResolver,
                               @Autowired(required = false) RagRuntimeProperties ragRuntimeProperties,
                               @Autowired(required = false) Tracer tracer) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.reasoningStrategy = reasoningStrategy;
        this.responseRanker = responseRanker;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;

        this.chatRequestSpecFactory = this::chatRequestSpec;
        QueryRuntimeComponents components = QueryRuntimeComponentsFactory.create(
                featureConfig,
                toolsConfig,
                expander,
                analyser,
                nerQueryEnricher,
                classifier,
                retriever,
                dateExistenceGuard,
                meetingMinutesToolsAdapter,
                postRetrievalProcessor,
                responseValidator,
                questionAnswerAdvisor,
                chatRequestSpecFactory,
                naiveCorpusContextService,
                ragRuntimeProperties);
        this.queryInputPreparer = components.queryInputPreparer();
        this.responseSynthesisPipeline = components.responseSynthesisPipeline();
        this.modelCatalogPort = modelCatalogPort;
        this.chatScopedRagConfigResolver = chatScopedRagConfigResolver;
        this.tracer = tracer;
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
            applyChatModelGovernance(chatModel);
            return generateResponseInternal(query, null);
        } finally {
            REQUEST_CHAT_MODEL.remove();
        }
    }

    /**
     * RAG query scoped to a user/project/conversation (chat). Uses {@link RagExecutionContextHolder}
     * for retrieval filters and config resolution.
     */
    @Transactional(readOnly = true)
    public QueryResponse generateResponseForChat(String query, String chatModel, UUID userId, UUID projectId,
                                                 UUID conversationId, List<String> documentFilter) {
        try {
            applyChatModelGovernance(chatModel);
            List<String> filter = (documentFilter == null || documentFilter.isEmpty())
                    ? List.of(RagExecutionContext.ALL_DOCUMENTS)
                    : documentFilter;
            RagExecutionContext overlay = new RagExecutionContext(
                    conversationId != null ? conversationId.toString() : null,
                    userId != null ? userId.toString() : null,
                    projectId != null ? projectId.toString() : null,
                    null,
                    filter,
                    null);
            return generateResponseInternal(query, overlay);
        } finally {
            REQUEST_CHAT_MODEL.remove();
        }
    }

    private void applyChatModelGovernance(String chatModel) {
        if (chatModel == null || chatModel.isBlank()) {
            return;
        }
        try {
            String validated =
                    EffectiveModelPolicy.validateChatModelOverride(
                            chatModel, modelCatalogPort.allowedLlmNamesInGovernance());
            REQUEST_CHAT_MODEL.set(validated);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private QueryResponse generateResponseInternal(String query, RagExecutionContext contextOverlay) {

        try {
            String traceId = Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                    .orElseGet(() -> UUID.randomUUID().toString());
            RagConfig resolved = chatScopedRagConfigResolver.resolveForExecutionContext(contextOverlay);
            RagExecutionContext ctx = buildContext(contextOverlay, resolved, traceId);
            RagExecutionContextHolder.set(ctx);

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
            if (RagEffectiveFeatures.reasoningEnabled(featureConfig) && reasoningStrategy != null) {
                preOutput = reasoningStrategy.runPreStep(query, pq.queryType(), pq.nerEntities(), pq.expandedQuery());
            }

            CoreSynthesisResult core = responseSynthesisPipeline.synthesizeCore(pq, preOutput);
            return finalizeResponseWithOptionalReasoning(query, pq, core);
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
            log().error(LOG_STACK_TRACE, e);
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
            log().error(LOG_STACK_TRACE, e);
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
            log().error(LOG_STACK_TRACE, e);
            String errorResponse = generateErrorResponse(query, e);
            return QueryResponse.fromLLM(errorResponse);
        } finally {
            RagExecutionContextHolder.clear();
        }
    }

    private QueryResponse finalizeResponseWithOptionalReasoning(
            String query, PreparedQuery pq, CoreSynthesisResult core) {
        if (!RagEffectiveFeatures.reasoningEnabled(featureConfig) || reasoningStrategy == null) {
            return core.toDirectQueryResponse(pq.queryType());
        }
        DraftAndContext dac = core.draftAndContext();
        if (dac.draft() == null || dac.draft().trim().isEmpty()) {
            return core.toDirectQueryResponse(pq.queryType());
        }
        if (!isDraftAcceptable(dac)) {
            log().info("Draft not acceptable (A.5 fallback), using plain LLM");
            String answer = responseSynthesisPipeline.fallbackPlainLlm(pq);
            return QueryResponse.fromLLM(answer != null ? answer : dac.draft(), pq.queryType());
        }
        PostStepOutput postOutput = reasoningStrategy.runPostStep(query, dac.context(), dac.draft());
        String candidate =
                (postOutput != null && postOutput.verifiedOrRefinedText() != null)
                        ? postOutput.verifiedOrRefinedText()
                        : dac.draft();
        List<CandidateResponse> candidates = new ArrayList<>();
        candidates.add(CandidateResponse.of(candidate, "reasoning"));
        if (RagEffectiveFeatures.rankerEnabled(featureConfig) && responseRanker != null) {
            RankerResult rankerResult = responseRanker.selectBest(query, dac.context(), candidates);
            if (rankerResult != null && rankerResult.chosenText() != null) {
                return QueryResponse.fromTool(rankerResult.chosenText(), "reasoning+ranker", pq.queryType());
            }
        }
        return QueryResponse.fromTool(candidate, "reasoning", pq.queryType());
    }

    private static RagExecutionContext buildContext(RagExecutionContext overlay, RagConfig resolved, String traceId) {
        if (overlay == null) {
            return RagExecutionContext.forLegacyPipeline(resolved, traceId);
        }
        return new RagExecutionContext(
                overlay.conversationId(),
                overlay.userId(),
                overlay.projectId(),
                resolved,
                overlay.documentFilter(),
                traceId);
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
        return !(lower.contains("no puedo encontrar") || lower.contains("no encontrado") || lower.contains("no se encontró") || lower.contains("no hay información"));
    }
}
