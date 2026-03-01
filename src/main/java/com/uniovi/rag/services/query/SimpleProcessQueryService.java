package com.uniovi.rag.services.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.AbstractContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimpleProcessQueryService implements QueryService {

    protected static final String PROMPT_TEMPLATE = """
        You are a helpful assistant that answers questions based on retrieved documents from a meeting minutes database.
        
        CRITICAL: The following context contains RAW DOCUMENT FRAGMENTS retrieved from the database.
        Base your answer ONLY on the information provided in the context.
        
        RULES:
        1. If the context is empty or does not contain enough information, clearly state that you cannot find the information. DO NOT invent, guess, or make up information.
        2. NEVER invent names, dates, places, actas, or any other information not explicitly in the context. Do not invent acta dates or mix information from different actas.
        3. Answer in the SAME LANGUAGE as the user's question.
        4. Be concise but complete. Do not add headers or explanatory text - provide the direct answer.
        
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

    public SimpleProcessQueryService(RagFeatureConfiguration featureConfig,
                                     RagToolsConfiguration toolsConfig,
                                     QueryExpander expander,
                                     QueryAnalyser analyser,
                                     QueryClassifier classifier,
                                     ContextRetriever retriever,
                                     ChatClient chatClient) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.expander = expander;
        this.analyser = analyser;
        this.classifier = classifier;
        this.retriever = retriever;
        this.toolsConfig = toolsConfig;
    }

    @Override
    public QueryResponse generateResponse(String query) {
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
    }

    private String askModel(String query, JSONObject nerEntities) {
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
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String generateNoContextResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant information was found in the available documents to answer this question.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            apologizing and stating that no relevant information was found.
            Be concise and polite. Do not repeat the question.
            """, query != null ? query : "");
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log().warn("Error generating no-context response, using fallback: {}", e.getMessage());
            return "No se encontró información relevante en los documentos disponibles para responder a esta pregunta.";
        }
    }
}


