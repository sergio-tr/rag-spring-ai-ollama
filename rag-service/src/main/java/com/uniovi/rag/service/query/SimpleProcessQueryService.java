package com.uniovi.rag.service.query;

import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.AbstractContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimpleProcessQueryService implements QueryService {

    protected static final String PROMPT_TEMPLATE = """
        You are a helpful assistant. Retrieved fragments from a meeting-minutes database are provided below when available.
        
        PRIORITY — answer the user's question:
        - If the question is general (jokes, definitions, chat, etc.) OR the context does not help answer it, answer directly using general knowledge in the user's language. Do not ask for more context.
        - If the question is about the documents AND the context is relevant, base factual claims on the context only; never invent acta-specific facts not in the context.
        
        RULES for document-specific answers:
        1. NEVER invent names, dates, places, actas, or facts not in the context when answering about meetings.
        2. Answer in the SAME LANGUAGE as the user's question.
        3. Be concise. Do not add unnecessary headers.
        
        <Question> %s </Question>
        <Context> %s </Context>
        
        Provide your direct answer now (in the same language as the question):
        """;


    protected final RagFeatureConfiguration featureConfig;
    protected final ChatClient chatClient;
    protected final QueryExpander expander;
    protected final QueryAnalyser analyser;
    protected final QueryClassifier classifier;
    protected final ContextRetriever retriever;
    protected final RagToolsConfiguration toolsConfig;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    private static final ThreadLocal<String> REQUEST_CHAT_MODEL = new ThreadLocal<>();

    public SimpleProcessQueryService(RagFeatureConfiguration featureConfig,
                                     RagToolsConfiguration toolsConfig,
                                     QueryExpander expander,
                                     QueryAnalyser analyser,
                                     QueryClassifier classifier,
                                     ContextRetriever retriever,
                                     ChatClient chatClient,
                                     OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.expander = expander;
        this.analyser = analyser;
        this.classifier = classifier;
        this.retriever = retriever;
        this.toolsConfig = toolsConfig;
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

    @Override
    public QueryResponse generateResponse(String query, String chatModel) {
        try {
            if (chatModel != null && !chatModel.isBlank()) {
                REQUEST_CHAT_MODEL.set(chatModel.trim());
            }
            ollamaConnectivityChecker.prepareForQuery(chatModel);

        String finalQuery = featureConfig.isExpansionEnabled() ? expander.expand(query) : query;
        JSONObject nerEntities = featureConfig.isNerEnabled() ? analyser.analyse(finalQuery) : null;
        QueryType queryType = featureConfig.isToolsEnabled() ? classifier.classify(finalQuery) : null;

        if (queryType != null) {
            Tool tool = toolsConfig.getTool(queryType);

            try {
                ToolResult toolResult = tool.execute(ToolExecutionContext.of(finalQuery, queryType, nerEntities));
                if (toolResult != null && toolResult.result() != null) {
                    return QueryResponse.fromTool(toolResult.result(), toolResult.source(), queryType);
                }
            } catch (Exception e) {
                log().error("Error executing tool: {}", e.getMessage());
            }

        }

        String answer = askModel(finalQuery, nerEntities);
        return QueryResponse.fromLLM(answer, queryType);
        } finally {
            REQUEST_CHAT_MODEL.remove();
        }
    }

    private String askModel(String query, JSONObject nerEntities) {
        if (!featureConfig.isUseRetrieval()) {
            try {
                String content = chatRequestSpec().user(query).call().content();
                return content != null && !content.trim().isEmpty() ? content : generateNoContextResponse(query);
            } catch (Exception e) {
                log().warn("LLM call without context failed: {}", e.getMessage());
                return generateNoContextResponse(query);
            }
        }
        if (GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly(query)) {
            try {
                String content = chatRequestSpec().user(query).call().content();
                if (content != null && !content.trim().isEmpty()) {
                    log().info("SimpleProcessQueryService: direct LLM (general-knowledge routing), skipping retrieval");
                    return content;
                }
            } catch (Exception e) {
                log().warn("Direct general-knowledge LLM failed, falling back to RAG path: {}", e.getMessage());
            }
        }
        List<Document> docs;
        if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
            docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(query, nerEntities);
        } else {
            docs = retriever.retrieve(query);
        }
        String context = retriever.createContext(docs, query, nerEntities);
        if (context == null || context.trim().isEmpty()) {
            return generateNoContextResponse(query);
        }
        String prompt = String.format(PROMPT_TEMPLATE, query, context);
        return chatRequestSpec()
                .user(prompt)
                .call()
                .content();
    }

    private String generateNoContextResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No document text was retrieved (empty vector store or no matches).
            
            Answer the question helpfully in the EXACT SAME LANGUAGE as the question,
            using general knowledge where appropriate.
            Do not repeat the question verbatim.
            """, query != null ? query : "");
        try {
            return chatRequestSpec().user(prompt).call().content();
        } catch (Exception e) {
            log().warn("Error generating no-context response, using fallback: {}", e.getMessage());
            return "I could not generate a response. Please try again.";
        }
    }
}


