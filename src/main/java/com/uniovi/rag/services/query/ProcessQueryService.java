package com.uniovi.rag.services.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.retriever.AbstractContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProcessQueryService implements QueryService {

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        You are a helpful assistant that answers questions based on retrieved documents from a meeting minutes database.
        
        CRITICAL: The following context contains RAW DOCUMENT FRAGMENTS retrieved from the database. 
        These are NOT pre-extracted answers - they are document fragments that may or may not contain 
        the information needed to answer the question.
        
        Your task is to:
        1. ANALYZE and PROCESS the retrieved context fragments
        2. EXTRACT the specific information needed to answer the question
        3. SYNTHESIZE a clear, direct answer from the relevant information found
        4. Answer in the SAME LANGUAGE as the user's question (if Spanish, answer in Spanish; if English, answer in English, etc.)
        
        IMPORTANT INSTRUCTIONS:
        1. You must PROCESS the context - do not just copy or summarize it. Extract the specific answer.
        2. Base your answer ONLY on the information provided in the context
        3. If the context does not contain enough information to answer the question, clearly state that in the same language as the question
        4. Be concise but complete - provide all relevant information from the context
        5. Do not add information that is not in the context
        6. Do not include headers, introductions, or explanatory text - just provide the direct answer
        7. If multiple fragments contain relevant information, synthesize them into a coherent answer
        
        <QueryType> %s </QueryType>
        <Question> %s </Question>
        <Context> %s </Context>
        
        Provide your direct answer now (in the same language as the question):
        """;

    private final RagFeatureConfiguration featureConfig;
    private final ChatClient chatClient;
    private final QueryExpander expander;
    private final QueryAnalyser analyser;
    private final QueryClassifier classifier;
    private final ContextRetriever retriever;
    private final RagToolsConfiguration toolsConfig;

    public ProcessQueryService(RagFeatureConfiguration featureConfig,
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
    public String generateResponse(String query) {
        try {
            String expandedQuery = expand(query);
            JSONObject nerEntities = analyse(expandedQuery);
            QueryType queryType = classify(expandedQuery);

            log().debug("Query expanded: {}", expandedQuery);
            log().debug("NER: {}", nerEntities);
            log().debug("Query Type : {}", queryType);

            ToolResult response = tryToolRoute(expandedQuery, nerEntities, queryType);

            if (response == null) {
                String answer = askModel(expandedQuery, nerEntities, queryType);
                log().info("Response generated with with model directly: {}", answer);
                return answer;
            }


            log().info("Response generated with tool {}: {}", response.source(), response.result());
            return response.result();
        } catch (Exception e) {
            log().error("Unexpected error processing query : {}", query, e);
            return generateErrorResponse(query);
        }
    }
    
    /**
     * Generates an error response in the same language as the query.
     */
    private String generateErrorResponse(String query) {
        // Detect query language (simple heuristic)
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("quién") || queryLower.contains("qué") || 
                           queryLower.contains("cuándo") || queryLower.contains("dónde") ||
                           queryLower.contains("cuántos") || queryLower.contains("cómo");
        
        if (isSpanish) {
            return "Lo siento, ocurrió un error al procesar tu consulta. Por favor, inténtalo de nuevo.";
        } else {
            return "I'm sorry, an error occurred while processing your query. Please try again.";
        }
    }

    private String expand(String query) {
        return featureConfig.isExpansionEnabled() ? expander.expand(query) : query;
    }

    private JSONObject analyse(String query) {
        return featureConfig.isNerEnabled() ? analyser.analyse(query) : null;
    }

    private QueryType classify(String query) {
        return featureConfig.isToolsEnabled() ? classifier.classify(query) : null;
    }

    private ToolResult tryToolRoute(String query, JSONObject nerEntities, QueryType queryType) {
        if (!featureConfig.isToolsEnabled() || queryType == null) return null;

        Tool tool = toolsConfig.getTool(queryType);
        if (tool == null) {
            log().debug("");
            return null;
        }

        try {
            log().debug("Executing tool : {}", queryType);
            return featureConfig.isNerEnabled() ?
                    tool.execute(ToolExecutionContext.of(query, queryType, nerEntities)) :
                    tool.execute(ToolExecutionContext.of(query, queryType));
        } catch (Exception e) {
            log().warn("Error executing tool {}: {}", queryType, e.getMessage());
            return null;
        }
    }

    private String askModel(String query, JSONObject nerEntities, QueryType queryType) {
        List<Document> docs;
        if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
            docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(query, nerEntities);
            log().debug("Using optimized retrieval with metadata filters, retrieved {} documents", docs.size());
        } else {
            docs = retriever.retrieve(query);
        }

        String context = retriever.createContext(docs, query, nerEntities);

        log().debug("Retrieved {} documents, context length: {}", docs.size(), context.length());
        log().debug("Retrieved context:\n{}", context);

        if (context == null || context.trim().isEmpty()) {
            log().warn("Empty context retrieved for query: {}", query);
            // Generate a helpful message in the same language as the query
            return generateNoContextResponse(query);
        }

        String prompt = String.format(
                DEFAULT_PROMPT_TEMPLATE,
                queryType != null ? queryType.name() : "UNKNOWN",
                query,
                context
        );

        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();
            
            // Validate response is not empty
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM for query: {}", query);
                return generateNoContextResponse(query);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating response for query: {}", query, e);
            return generateNoContextResponse(query);
        }
    }
    
    /**
     * Generates a response when no context is available.
     * The response language matches the query language.
     */
    private String generateNoContextResponse(String query) {
        // Detect query language (simple heuristic)
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("quién") || queryLower.contains("qué") || 
                           queryLower.contains("cuándo") || queryLower.contains("dónde") ||
                           queryLower.contains("cuántos") || queryLower.contains("cómo");
        
        if (isSpanish) {
            return "Lo siento, no se encontró información relevante en los documentos disponibles para responder a tu pregunta.";
        } else {
            return "I'm sorry, I couldn't find relevant information in the available documents to answer your question.";
        }
    }
}

