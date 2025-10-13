package com.uniovi.rag.services.query;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import com.uniovi.rag.services.tools.agentic.AgenticToolsManager;
import com.uniovi.rag.services.tools.agentic.AgenticTool;

import org.json.JSONObject;
import java.util.List;
import java.util.Map;

@Service
public class AgenticQueryService implements QueryService {

    private final ChatClient agenticChatClient;
    private final RagFeatureConfiguration featureConfig;
    private final QueryExpander expander;
    private final QueryAnalyser analyser;
    private final QueryClassifier classifier;
    private final RagToolsConfiguration toolsConfig;
    private final AgenticToolsManager agenticToolsManager;

    public AgenticQueryService(
        ChatClient agenticChatClient,
        RagFeatureConfiguration featureConfig,
        QueryExpander expander,
        QueryAnalyser analyser,
        QueryClassifier classifier,
        VectorStore vectorStore,
        RagToolsConfiguration toolsConfig,
        AgenticToolsManager agenticToolsManager
    ) {
        this.agenticChatClient = agenticChatClient;
        this.featureConfig = featureConfig;
        this.expander = expander;
        this.analyser = analyser;
        this.classifier = classifier;
        this.toolsConfig = toolsConfig;
        this.agenticToolsManager = agenticToolsManager;
    }

    @Override
    public String generateResponse(String query) {
        String expandedQuery = featureConfig.isExpansionEnabled() ? expander.expand(query) : query;
        JSONObject nerEntities = featureConfig.isNerEnabled() ? analyser.analyse(expandedQuery) : null;
        QueryType queryType = featureConfig.isToolsEnabled() ? classifier.classify(expandedQuery) : null;

        // Estrategia agéntica mejorada
        String response = tryAgenticApproach(expandedQuery, nerEntities, queryType);
        
        if (response != null && !response.trim().isEmpty()) {
            return response;
        }

        // Fallback: usar el modelo directamente
        return useDirectModelApproach(expandedQuery, nerEntities, queryType);
    }

    private String tryAgenticApproach(String query, JSONObject nerEntities, QueryType queryType) {
        // Estrategia 1: Usar AgenticTools si están disponibles
        if (featureConfig.isToolsEnabled()) {
            String agenticResponse = tryAgenticTools(query, nerEntities);
            if (agenticResponse != null) {
                return agenticResponse;
            }
        }

        // Estrategia 2: Usar tool específica si está disponible
        if (featureConfig.isToolsEnabled() && queryType != null) {
            String toolResponse = trySpecificTool(query, queryType, nerEntities);
            if (toolResponse != null) {
                return toolResponse;
            }
        }

        // Estrategia 3: Intentar múltiples tools relevantes
        String multiToolResponse = tryMultipleTools(query, nerEntities);
        if (multiToolResponse != null) {
            return multiToolResponse;
        }

        // Estrategia 4: Usar el modelo con contexto mejorado
        return useEnhancedModelApproach(query, nerEntities, queryType);
    }

    private String tryAgenticTools(String query, JSONObject nerEntities) {
        try {
            // Intentar con la mejor tool agéntica
            Map<String, Object> result = agenticToolsManager.executeBestTool(query, nerEntities);
            
            if (Boolean.TRUE.equals(result.get("success"))) {
                return result.get("result").toString();
            }
            
            // Si falla, intentar con múltiples tools
            result = agenticToolsManager.executeMultipleTools(query, nerEntities, 3);
            
            if (Boolean.TRUE.equals(result.get("success"))) {
                return result.get("result").toString();
            }
            
        } catch (Exception e) {
            System.err.println("Error ejecutando agentic tools: " + e.getMessage());
        }
        
        return null;
    }

    private String trySpecificTool(String query, QueryType queryType, JSONObject nerEntities) {
        Tool tool = toolsConfig.getTool(queryType);
        if (tool != null) {
            try {
                ToolResult toolResult = tool.execute(
                    ToolExecutionContext.of(query, queryType, nerEntities)
                );
                if (toolResult != null && toolResult.result() != null) {
                    return toolResult.result();
                }
            } catch (Exception e) {
                System.err.println("Error ejecutando tool específica: " + e.getMessage());
            }
        }
        return null;
    }

    private String tryMultipleTools(String query, JSONObject nerEntities) {
        // Intentar con tools que podrían ser relevantes basándose en el contenido de la query
        QueryType[] relevantTypes = {
            QueryType.COUNT_DOCUMENTS,
            QueryType.FIND_PARAGRAPH,
            QueryType.EXTRACT_ENTITIES,
            QueryType.SUMMARIZE_TOPIC,
            QueryType.BOOLEAN_QUERY
        };

        for (QueryType type : relevantTypes) {
            Tool tool = toolsConfig.getTool(type);
            if (tool != null) {
                try {
                    ToolResult result = tool.execute(
                        ToolExecutionContext.of(query, type, nerEntities)
                    );
                    if (result != null && result.result() != null && !result.result().contains("No se encontraron")) {
                        return result.result();
                    }
                } catch (Exception e) {
                    // Continuar con la siguiente tool
                }
            }
        }
        return null;
    }

    private String useEnhancedModelApproach(String query, JSONObject nerEntities, QueryType queryType) {
        String userPrompt = """
            Pregunta: %s
            Entidades detectadas: %s
            Tipo de consulta sugerido: %s
            
            Por favor, proporciona una respuesta detallada y útil basada en la información disponible.
            Si la pregunta requiere información específica de las actas, indícalo claramente.
        """.formatted(query, nerEntities, queryType);

        String response = agenticChatClient
            .prompt()
            .system("""
                Eres un asistente agéntico que sirve como intermediario entre el usuario y la base de conocimiento sobre actas de reuniones. 
                Responde en el mismo idioma que la pregunta.
            """)
            .user(userPrompt)
            .call()
            .content()
            .trim();

        if (featureConfig.isValidationEnabled() && !evaluateResponse(query, nerEntities, queryType, response)) {
            return improveResponse(query, nerEntities, queryType, response);
        }

        return response;
    }

    private String useDirectModelApproach(String query, JSONObject nerEntities, QueryType queryType) {
        String userPrompt = """
            Pregunta: %s
            Entidades: %s
            Tipo de consulta: %s
            
            Por favor, responde a esta consulta utilizando las herramientas disponibles cuando sea apropiado.
        """.formatted(query, nerEntities, queryType);

        String response = agenticChatClient
            .prompt()
            .system("""
                Eres un asistente agéntico que sirve como intermediario entre el usuario y la base de conocimiento sobre actas de reuniones. 
                Responde en el mismo idioma que la pregunta.
            """)
            .user(userPrompt)
            .call()
            .content()
            .trim();

        if (featureConfig.isValidationEnabled() && !evaluateResponse(query, nerEntities, queryType, response)) {
            return improveResponse(query, nerEntities, queryType, response);
        }

        return response;
    }

    private String improveResponse(String query, JSONObject nerEntities, QueryType queryType, String originalResponse) {
        String improvedPrompt = """
            La siguiente respuesta no ha pasado la validación. Por favor, genera una nueva respuesta más precisa y relevante:
            Pregunta original: %s
            Entidades detectadas: %s 
            Tipo de consulta: %s
            Respuesta anterior: %s
        """.formatted(query, nerEntities, queryType, originalResponse);

        String improvedResponse = agenticChatClient
            .prompt()
            .user(improvedPrompt)
            .call()
            .content()
            .trim();

        if (!evaluateResponse(query, nerEntities, queryType, improvedResponse)) {
            String errorPrompt = """
                Genera un mensaje de error indicando que no se pudo generar una respuesta satisfactoria y pidiendo reformular la pregunta.
                Responde en el mismo idioma que esta pregunta: %s
            """.formatted(query);
            return agenticChatClient.prompt().user(errorPrompt).call().content().trim();
        }

        return improvedResponse;
    }

    private boolean evaluateResponse(String query, JSONObject nerEntities, QueryType queryType, String response) {
        String evalPrompt = """
            Eres un evaluador crítico. 
            Dada la pregunta, las entidades, el tipo de consulta y la respuesta generada, responde solo 'sí' si la respuesta es correcta, útil y relevante, o 'no' en caso contrario.
            Pregunta: %s
            Entidades: %s
            Tipo de consulta: %s
            Respuesta: %s
        """.formatted(query, nerEntities, queryType, response);

        String evalResult = agenticChatClient
            .prompt()
            .user(evalPrompt)
            .call()
            .content()
            .trim();
        return evalResult.toLowerCase().startsWith("s");
    }
}