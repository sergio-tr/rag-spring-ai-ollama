package com.uniovi.rag.service.query;

import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.ContextRetriever;

import org.json.JSONObject;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimpleQueryService implements QueryService {

    private static final String QUESTION_PLACEHOLDER = "__QUESTION__";
    private static final String CONTEXT_PLACEHOLDER = "__CONTEXT__";
    protected static final String PROMPT_TEMPLATE = "You are a helpful assistant that answers questions based on retrieved documents from a meeting minutes database. Base your answer ONLY on the information provided in the context below. RULES: If the context is empty or does not contain enough information, clearly state that you cannot find the information. DO NOT invent, guess, or make up information. NEVER invent names, dates, places, actas, or any other information not explicitly in the context. Answer in the SAME LANGUAGE as the user's question. Be concise. Do not repeat the question. Question: " + QUESTION_PLACEHOLDER + " Context: " + CONTEXT_PLACEHOLDER + " Provide your direct answer now:";

    protected final ChatClient chatClient;
    protected final QueryExpander expander;
    protected final QueryAnalyser analyser;
    protected final ContextRetriever retriever;

    public SimpleQueryService(QueryExpander expander, QueryAnalyser analyser, ContextRetriever retriever, ChatClient chatClient) {
        this.chatClient = chatClient;
        this.expander = expander;
        this.analyser = analyser;
        this.retriever = retriever;
    }

    protected String askQueryToLlama(String query) {
        try {
            String content = chatClient.prompt().user(query).call().content();
            return content != null ? content : "";
        } catch (Exception e) {
            log().warn("ChatClient call failed: {}", e.getMessage());
            return null;
        }
    }

    /** Not used; kept for compatibility. Returns -1. */
    protected static int countTokens(String text) {
        return -1;
    }

    @Override
    public QueryResponse generateResponse(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula, vacia o solo espacios en blanco.");
        }

        String expandedQuery = expander.expand(question);

        JSONObject nerEntities = analyser.analyse(question);

        List<Document> docs;
        if (nerEntities != null && !nerEntities.isEmpty()) {
            docs = retriever.retrieveWithMetadataFilters(expandedQuery, nerEntities);
        } else {
            docs = retriever.retrieve(expandedQuery);
        }
        String context = retriever.createContext(docs, expandedQuery, nerEntities);

        if (context == null || context.trim().isEmpty()) {
            return QueryResponse.fromLLM("No relevant information was found in the available documents to answer this question.");
        }

        String template = PROMPT_TEMPLATE
                .replace(QUESTION_PLACEHOLDER, question)
                .replace(CONTEXT_PLACEHOLDER, context);

        log().info("\n\n-----------------------------------------------------------------------------");
        log().info("-----------------------------------------------------------------------------");
        log().info("QUERY: Pregunta final: " + template);
        log().info("\n\n-----------------------------------------------------------------------------");
        log().info("-----------------------------------------------------------------------------");

        return QueryResponse.fromLLM(askQueryToLlama(template));
    }

}