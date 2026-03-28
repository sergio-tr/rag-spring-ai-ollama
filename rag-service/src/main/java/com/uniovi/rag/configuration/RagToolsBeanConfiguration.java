package com.uniovi.rag.configuration;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.observability.TracedMeetingMinutesToolsAdapter;
import com.uniovi.rag.observability.TracedTool;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.*;
import com.uniovi.rag.tool.metadata.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class RagToolsBeanConfiguration {

    @Bean
    public RagToolsConfiguration toolsConfig(Map<QueryType, Tool> tools) {
        return new RagToolsConfiguration(tools);
    }

    @Bean
    public MeetingMinutesToolsAdapter meetingMinutesToolsAdapter(
            RagToolsConfiguration toolsConfig,
            QueryAnalyser queryAnalyser,
            @Autowired(required = false) ObservabilitySupport observability) {
        if (observability != null) {
            return new TracedMeetingMinutesToolsAdapter(toolsConfig, queryAnalyser, observability);
        }
        return new MeetingMinutesToolsAdapter(toolsConfig, queryAnalyser);
    }

    @Bean
    public Map<QueryType, Tool> tools(
            RagFeatureConfiguration featureConfig,
            ContextRetriever retriever,
            ChatClient chatClient,
            DocumentContentExtractor documentContentExtractor,
            MetadataLlmResponseCacheService metadataLlmResponseCacheService,
            @Autowired(required = false) ObservabilitySupport observability
    ) {
        Map<QueryType, Tool> tools = new HashMap<>();
        if (featureConfig.isMetadataEnabled()) {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new MetadataCountDocumentsTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.FIND_PARAGRAPH, new MetadataFindParagraphTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.COUNT_AND_EXPLAIN, new MetadataCountAndExplainTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.EXTRACT_ENTITIES, new MetadataExtractEntitiesTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.SUMMARIZE_TOPIC, new MetadataSummarizeTopicTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.BOOLEAN_QUERY, new MetadataBooleanQueryTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new MetadataCompareTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.GET_DURATION, new MetadataGetDurationTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.GET_FIELD, new MetadataGetFieldTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.FILTER_AND_LIST, new MetadataFilterAndListTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.DECISION_EXTRACTION, new MetadataDecisionExtractionTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService),
                    QueryType.SUMMARIZE_MEETING, new MetadataSummarizeMeetingTool(chatClient, retriever, documentContentExtractor, metadataLlmResponseCacheService)
            ));
        } else {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new CountDocumentsTool(chatClient, retriever, documentContentExtractor),
                    QueryType.FIND_PARAGRAPH, new FindParagraphTool(chatClient, retriever, documentContentExtractor),
                    QueryType.COUNT_AND_EXPLAIN, new CountAndExplainTool(chatClient, retriever, documentContentExtractor),
                    QueryType.EXTRACT_ENTITIES, new ExtractEntitiesTool(chatClient, retriever, documentContentExtractor),
                    QueryType.SUMMARIZE_TOPIC, new SummarizeTopicTool(chatClient, retriever, documentContentExtractor),
                    QueryType.BOOLEAN_QUERY, new BooleanQueryTool(chatClient, retriever, documentContentExtractor)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new CompareTool(chatClient, retriever, documentContentExtractor),
                    QueryType.GET_DURATION, new GetDurationTool(chatClient, retriever, documentContentExtractor),
                    QueryType.GET_FIELD, new GetFieldTool(chatClient, retriever, documentContentExtractor),
                    QueryType.FILTER_AND_LIST, new FilterAndListTool(chatClient, retriever, documentContentExtractor),
                    QueryType.DECISION_EXTRACTION, new DecisionExtractionTool(chatClient, retriever, documentContentExtractor),
                    QueryType.SUMMARIZE_MEETING, new SummarizeMeetingTool(chatClient, retriever, documentContentExtractor)
            ));
        }
        if (observability != null) {
            return tools.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new TracedTool(e.getValue(), observability)));
        }
        return tools;
    }
}
