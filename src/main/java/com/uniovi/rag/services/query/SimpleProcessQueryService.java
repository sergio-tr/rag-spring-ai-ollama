package com.uniovi.rag.services.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SimpleProcessQueryService implements QueryService {

    protected static final String PROMPT_TEMPLATE = "La siguiente información en <contexto> ya ha sido extraída como respuesta directa a la <pregunta>" +
            "Tu única tarea es presentarla en forma de respuesta clara y breve en español. " +
            "No debes cuestionar, verificar ni rechazar la información. No añadas contexto adicional, ni justificaciones, ni comentarios.\n" +
            "<Pregunta> %s </Pregunta>\n" +
            "<Contexto> %s </Contexto>";


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
        String context = retriever.createContext(retriever.retrieve(query), query, nerEntities);
        String prompt = String.format(PROMPT_TEMPLATE, query, context);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}


