package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.DraftAndContext;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.GeneralKnowledgeQueryDetector;
import com.uniovi.rag.api.ConnectivityFailureDetector;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.retriever.AbstractContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Core LLM + retrieval generation used by {@link com.uniovi.rag.service.query.ProcessQueryService}
 * and by reasoning drafts. Keeps one place for RAG prompts, advisor path, and no-context fallbacks.
 *
 * <p><b>Stock {@link QuestionAnswerAdvisor} vs manual retrieval</b>: Spring AI's advisor performs
 * similarity search in the {@link org.springframework.ai.vectorstore.VectorStore} using the user's text.
 * When NER is active, we pass the same enriched query to {@code .user(...)} that in the manual path
 * ({@link NERQueryEnricher#buildEnrichedQueryForRetrieval}) so that the advisor's embedding search
 * benefits from the entities. The advisor <em>does not</em> apply {@link AbstractContextRetriever#retrieveWithMetadataFilters}
 * or {@link PostRetrievalProcessor}; if post-retrieval is globally active, the advisor path simply does not run
 * (the manual fallback does). Tools are resolved in {@link com.uniovi.rag.service.query.pipeline.ResponseSynthesisPipeline}
 * before this kernel.</p>
 */
public final class AnswerGenerationKernel {

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerationKernel.class);

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        You are a helpful assistant. Retrieved RAW DOCUMENT FRAGMENTS from a meeting-minutes database appear below when retrieval runs.
        They are NOT pre-extracted answers — fragments may or may not relate to the user's question.
        
        Your task is to:
        1. Decide whether the question is about those documents or is general / unrelated.
        2. If document-related and the context is relevant: ANALYZE fragments, EXTRACT facts, SYNTHESIZE a direct answer.
        3. If general or context is irrelevant: answer the question directly (see PRIORITY below).
        4. Answer in the SAME LANGUAGE as the user's question.
        
        CRITICAL RULES for document-specific answers (meetings, actas, uploaded content):
        1. When the context is relevant, PROCESS it — do not just copy; extract the specific answer.
        2. Base factual claims about meetings/documents ONLY on information present in the context; never invent acta-specific facts.
        3. If the question asks for document facts but the context truly lacks them, say you cannot find that information in the available documents — do not invent.
        4. NEVER invent names, dates, places, actas, or meeting facts not explicitly supported by the context when answering about documents.
        5. Do not fabricate lists of attendees or details not present in the context for document-style questions.
        6. If the question is document-specific and the context lacks the requested facts, say so clearly (same language).
        7. When the context applies: be concise but complete; synthesize multiple fragments; cite source/date when useful.
        8. Do not include unnecessary headers — give the direct answer.
        
        PRIORITY — answer the user's question first:
        - If the question is general knowledge, conversational, or clearly NOT about meeting minutes (e.g. jokes, definitions, math, small talk), OR the retrieved fragments do NOT contain information that helps answer the question, answer directly using general knowledge in the user's language. Do NOT ask the user for more context. Do NOT refuse to answer only because the fragments are unrelated or off-topic.
        - When the question IS about the documents/meetings AND the context contains relevant material, follow the CRITICAL RULES above: base factual claims on the context only; never invent acta-specific names, dates, or facts not present in the context.
        
        <QueryType> %s </QueryType>
        <Question> %s </Question>
        <Context> %s </Context>
        
        Provide your direct answer now (in the same language as the question):
        """;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;

    private final RagFeatureConfiguration featureConfig;
    private final NERQueryEnricher nerQueryEnricher;
    private final ContextRetriever retriever;
    private final PostRetrievalProcessor postRetrievalProcessor;
    private final ResponseValidator responseValidator;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final ChatRequestSpecFactory chatRequestSpecFactory;

    public AnswerGenerationKernel(
            RagFeatureConfiguration featureConfig,
            NERQueryEnricher nerQueryEnricher,
            ContextRetriever retriever,
            PostRetrievalProcessor postRetrievalProcessor,
            ResponseValidator responseValidator,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            ChatRequestSpecFactory chatRequestSpecFactory) {
        this.featureConfig = featureConfig;
        this.nerQueryEnricher = nerQueryEnricher;
        this.retriever = retriever;
        this.postRetrievalProcessor = postRetrievalProcessor;
        this.responseValidator = responseValidator;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.chatRequestSpecFactory = chatRequestSpecFactory;
    }

    public DraftAndContext askModelWithPreStep(String query, JSONObject nerEntities, QueryType queryType, String preStepThought) {
        if (!featureConfig.isUseRetrieval()) {
            try {
                String response = chatRequestSpecFactory.spec().user(query).call().content();
                String validated = response != null ? responseValidator.validateAndClean(response, "ProcessQueryService-NoContext") : null;
                return new DraftAndContext(validated != null ? validated : generateNoContextResponse(query), "");
            } catch (Exception e) {
                log.warn("LLM call without context failed in reasoning path: {}", e.getMessage());
                return new DraftAndContext(generateNoContextResponse(query), "");
            }
        }
        if (GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly(query)) {
            try {
                String response = chatRequestSpecFactory.spec().user(query).call().content();
                String validated = response != null
                        ? responseValidator.validateAndClean(response, "ProcessQueryService-ReasoningDirect")
                        : null;
                if (validated != null && !validated.trim().isEmpty()) {
                    log.info("Reasoning path: direct LLM (general-knowledge routing), skipping retrieval");
                    return new DraftAndContext(validated, "");
                }
            } catch (Exception e) {
                log.warn("Direct general-knowledge LLM failed in reasoning path, falling back: {}", e.getMessage());
            }
        }
        String retrievalQuery = (featureConfig.isNerEnabled() && nerQueryEnricher != null && nerEntities != null && !nerEntities.isEmpty())
                ? nerQueryEnricher.buildEnrichedQueryForRetrieval(query, nerEntities)
                : query;
        List<Document> docs;
        try {
            if (retriever instanceof AbstractContextRetriever abstractRetriever && nerEntities != null && !nerEntities.isEmpty()) {
                docs = abstractRetriever.retrieveWithMetadataFilters(retrievalQuery, nerEntities);
            } else {
                docs = retriever.retrieve(retrievalQuery);
            }
        } catch (Exception e) {
            log.warn("Retrieval failed in reasoning path: {}", e.getMessage());
            return null;
        }
        if (featureConfig.isPostRetrievalEnabled() && postRetrievalProcessor != null) {
            docs = postRetrievalProcessor.process(docs, query);
        }
        String context = retriever.createContext(docs, query, nerEntities);
        if (context == null || context.trim().isEmpty()) {
            String fallback = generateNoContextResponse(query);
            return new DraftAndContext(fallback, "");
        }
        String contextWithReasoning = context;
        if (preStepThought != null && !preStepThought.trim().isEmpty()) {
            contextWithReasoning = "<Reasoning>\n" + preStepThought.trim() + "\n</Reasoning>\n\n" + context;
        }
        String prompt = String.format(DEFAULT_PROMPT_TEMPLATE,
                queryType != null ? queryType.name() : "UNKNOWN",
                query,
                contextWithReasoning);
        try {
            String response = chatRequestSpecFactory.spec().user(prompt).call().content();
            String validated = responseValidator.validateAndClean(response, "ProcessQueryService");
            if (validated != null) {
                return new DraftAndContext(validated, context);
            }
        } catch (Exception e) {
            log.warn("LLM call failed in reasoning path: {}", e.getMessage());
        }
        return new DraftAndContext(generateNoContextResponse(query), context);
    }

    public String askModel(String query, JSONObject nerEntities, QueryType queryType) {
        if (!featureConfig.isUseRetrieval()) {
            try {
                String rawContent = chatRequestSpecFactory.spec().user(query).call().content();
                String validated = rawContent != null ? responseValidator.validateAndClean(rawContent, "ProcessQueryService-NoContext") : null;
                if (validated != null && !validated.trim().isEmpty()) {
                    log.info("Response generated without context (use-retrieval=false)");
                    return validated;
                }
            } catch (Exception e) {
                log.warn("LLM call without context failed: {}", e.getMessage());
            }
            return generateNoContextResponse(query);
        }

        if (GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly(query)) {
            try {
                String rawContent = chatRequestSpecFactory.spec().user(query).call().content();
                String validated = rawContent != null
                        ? responseValidator.validateAndClean(rawContent, "ProcessQueryService-DirectGeneral")
                        : null;
                if (validated != null && !validated.trim().isEmpty()) {
                    log.info("Direct LLM (general-knowledge routing), skipping retrieval");
                    return validated;
                }
            } catch (Exception e) {
                log.warn("Direct general-knowledge LLM failed, falling back to RAG path: {}", e.getMessage());
            }
        }

        if (canUseStockQuestionAnswerAdvisorFastPath()) {
            String advisorQuery = effectiveQueryForAdvisor(query, nerEntities);
            if (!advisorQuery.equals(query)) {
                log.debug("QuestionAnswerAdvisor: NER-enriched query for embedding search ({} vs {} chars)",
                        advisorQuery.length(), query.length());
            }
            try {
                String rawContent = chatRequestSpecFactory.spec()
                        .user(advisorQuery)
                        .advisors(questionAnswerAdvisor)
                        .call()
                        .content();
                String validated = rawContent != null ? responseValidator.validateAndClean(rawContent, "ProcessQueryService-Advisor") : null;
                if (validated != null && !validated.trim().isEmpty()) {
                    log.info("Response generated via QuestionAnswerAdvisor (askModel path)");
                    return validated;
                }
            } catch (Exception e) {
                log.warn("QuestionAnswerAdvisor path failed, falling back to manual retrieve+createContext: {}", e.getMessage());
            }
        }

        boolean isProblematicConfig = featureConfig.isMetadataEnabled() &&
                featureConfig.isNerEnabled() &&
                !featureConfig.isToolsEnabled();

        String retrievalQuery = (featureConfig.isNerEnabled() && nerQueryEnricher != null && nerEntities != null && !nerEntities.isEmpty())
                ? nerQueryEnricher.buildEnrichedQueryForRetrieval(query, nerEntities)
                : query;
        if (!retrievalQuery.equals(query)) {
            log.debug("Using NER-enriched query for retrieval (length {} vs {} chars)", retrievalQuery.length(), query.length());
        }
        List<Document> docs;
        try {
            if (retriever instanceof AbstractContextRetriever acr && nerEntities != null && !nerEntities.isEmpty()) {
                if (isProblematicConfig) {
                    log.debug("Attempting retrieval with metadata filters and NER entities");
                }
                docs = acr.retrieveWithMetadataFilters(retrievalQuery, nerEntities);
                log.info("Using optimized retrieval with metadata filters, retrieved {} documents", docs.size());
            } else {
                if (isProblematicConfig) {
                    log.debug("Using standard retrieval (no metadata filters or NER)");
                }
                docs = retriever.retrieve(retrievalQuery);
            }
        } catch (NullPointerException e) {
            log.error("NullPointerException during document retrieval (config: metadata={}, ner={}): {}",
                    featureConfig.isMetadataEnabled(),
                    featureConfig.isNerEnabled(),
                    e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log.warn("Document retrieval failed (connectivity): {}", e.getMessage());
            } else {
                log.error("Exception during document retrieval (config: metadata={}, ner={}): {}",
                        featureConfig.isMetadataEnabled(),
                        featureConfig.isNerEnabled(),
                        e.getMessage(), e);
            }
            throw e;
        }

        if (featureConfig.isPostRetrievalEnabled() && postRetrievalProcessor != null) {
            docs = postRetrievalProcessor.process(docs, query);
        }
        String context = retriever.createContext(docs, query, nerEntities);

        log.info("Retrieved {} documents, context length: {}", docs.size(), context != null ? context.length() : 0);
        if (log.isDebugEnabled()) {
            log.info("Retrieved context:\n{}", context);
        }

        if (context == null || context.trim().isEmpty()) {
            log.warn("Empty context retrieved for query: {}", query);
            return generateNoContextResponse(query);
        }

        if (context.trim().length() < 50) {
            log.warn("Context too short ({} chars) for query: {}", context.length(), query);
        }

        String prompt = String.format(
                DEFAULT_PROMPT_TEMPLATE,
                queryType != null ? queryType.name() : "UNKNOWN",
                query,
                context
        );

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {} for query: {}", attempt, query);
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }

                String response = chatRequestSpecFactory.spec()
                        .user(prompt)
                        .call()
                        .content();

                String validatedResponse = responseValidator.validateAndClean(response, "ProcessQueryService");

                if (validatedResponse != null) {
                    log.info("Successfully generated response on attempt {}", attempt + 1);
                    return validatedResponse;
                } else {
                    log.warn("Invalid response from LLM on attempt {} for query: {}", attempt + 1, query);
                    if (attempt < MAX_RETRIES) {
                        continue;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted during retry for query: {}", query, e);
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("Error generating response on attempt {} for query: {} - {}",
                        attempt + 1, query, e.getMessage());
            }
        }

        if (lastException != null) {
            log.error("Failed to generate response after {} attempts for query: {}", MAX_RETRIES + 1, query, lastException);
        } else {
            log.error("Failed to generate valid response after {} attempts for query: {}", MAX_RETRIES + 1, query);
        }

        return generateNoContextResponse(query);
    }

    public String generateNoContextResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No document text was retrieved from the vector store for this request (empty or no matches).
            
            Answer the question helpfully and concisely in the EXACT SAME LANGUAGE as the question.
            Use general knowledge where appropriate (e.g. jokes, explanations, math).
            Only apologize for missing documents if the user explicitly asked for information that could only come from private uploaded files.
            Do not repeat the question verbatim.
            """, query != null ? query : "");

        try {
            String response = chatRequestSpecFactory.spec()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            // Fallback if LLM fails
        }

        return "I could not generate a response. Please try again.";
    }

    /**
     * Fast path with the stock {@link QuestionAnswerAdvisor}: requires bean and flag
     * {@code rag.features.use-advisor}. Post-retrieval does not block this path; if it is active,
     * the advisor path does not apply (advisor limitation). If the advisor fails or returns empty,
     * the manual retrieve + post-retrieval path is continued if applicable.
     */
    private boolean canUseStockQuestionAnswerAdvisorFastPath() {
        return featureConfig.isUseAdvisor() && questionAnswerAdvisor != null;
    }

    /**
     * Text that the advisor receives as "user message" (and uses internally for embedding search).
     * When NER is enabled and entities are present, matches the manual path retrieval query.
     */
    private String effectiveQueryForAdvisor(String query, JSONObject nerEntities) {
        if (featureConfig.isNerEnabled() && nerQueryEnricher != null && nerEntities != null && !nerEntities.isEmpty()) {
            return nerQueryEnricher.buildEnrichedQueryForRetrieval(query, nerEntities);
        }
        return query;
    }
}
