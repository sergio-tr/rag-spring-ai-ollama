package com.uniovi.rag.service.query;

import com.uniovi.rag.api.OllamaConnectivityChecker;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.ContextRetriever;

import org.json.JSONObject;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimpleQueryService implements QueryService {

    private static final String QUESTION_PLACEHOLDER = "__QUESTION__";
    private static final String CONTEXT_PLACEHOLDER = "__CONTEXT__";
    protected static final String PROMPT_TEMPLATE = "You are a helpful assistant. Retrieved meeting minutes fragments are in the context when available. PRIORITY: answer the user's question. If the question is general or the context does not help, answer from general knowledge in the user's language. If the question is about the documents and the context is relevant, base factual claims on the context only; never invent acta-specific facts. Context empty or irrelevant: still answer the question when possible. Question: " + QUESTION_PLACEHOLDER + " Context: " + CONTEXT_PLACEHOLDER + " Provide your direct answer now:";

    protected final ChatClient chatClient;
    protected final QueryExpander expander;
    protected final QueryAnalyser analyser;
    protected final ContextRetriever retriever;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    private static final ThreadLocal<String> REQUEST_CHAT_MODEL = new ThreadLocal<>();

    public SimpleQueryService(QueryExpander expander, QueryAnalyser analyser, ContextRetriever retriever, ChatClient chatClient,
                              OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.chatClient = chatClient;
        this.expander = expander;
        this.analyser = analyser;
        this.retriever = retriever;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
    }

    private ChatClient.ChatClientRequestSpec chatRequestSpec() {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        String m = REQUEST_CHAT_MODEL.get();
        if (m != null && !m.isBlank()) {
            spec = spec.options(OllamaOptions.builder().model(m.trim()).build());
        }
        return spec;
    }

    private String answerWithoutRetrievedContext(String question) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No document text was retrieved from the vector store (empty or no matches).
            
            Answer the question helpfully in the EXACT SAME LANGUAGE as the question,
            using general knowledge where appropriate.
            Do not repeat the question verbatim.
            """, question != null ? question : "");
        return askQueryToLlama(prompt);
    }

    protected String askQueryToLlama(String query) {
        try {
            String content = chatRequestSpec().user(query).call().content();
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
    public QueryResponse generateResponse(String question, String chatModel) {
        try {
            if (chatModel != null && !chatModel.isBlank()) {
                REQUEST_CHAT_MODEL.set(chatModel.trim());
            }
            if (question == null || question.trim().isEmpty()) {
                throw new IllegalArgumentException("La pregunta no puede ser nula, vacia o solo espacios en blanco.");
            }

            ollamaConnectivityChecker.prepareForQuery(chatModel);

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
            String direct = answerWithoutRetrievedContext(question);
            return QueryResponse.fromLLM(direct != null ? direct : "I could not generate a response. Please try again.");
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
        } finally {
            REQUEST_CHAT_MODEL.remove();
        }
    }

}