package com.uniovi.rag.services.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.tools.Tool;
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
        The following information in <context> has already been extracted as a direct answer to the <question>.
        Your only task is to present it as a clear and concise response in Spanish.
        You must not question, verify, or reject the information. Do not add any additional context, justifications, or comments.
        
        <QueryType> %s </QueryType>
        <Question> %s </Question>
        <Context> %s </Context>
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
            return "An error occurred. Please try again.";
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


        List<Document> docs = retriever.retrieve(query);

        String context = retriever.createContext(docs, query, nerEntities);

        log().debug("Retrieved context:\n{}", context);

        String prompt = String.format(
                DEFAULT_PROMPT_TEMPLATE,
                queryType != null ? queryType.name() : "DESCONOCIDA",
                query,
                context
        );

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .trim();
    }
}

