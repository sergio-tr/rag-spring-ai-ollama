package com.uniovi.rag.configuration;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.observability.TracedMeetingMinutesToolsAdapter;
import com.uniovi.rag.observability.TracedTool;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.ToolRagService;
import com.uniovi.rag.tool.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RagToolsBeanConfiguration}.
 */
class RagToolsBeanConfigurationTest {

    @Test
    void toolsConfigBean_createsRagToolsConfiguration() {
        RagToolsBeanConfiguration config = new RagToolsBeanConfiguration();
        Map<QueryType, Tool> tools = Map.of();
        RagToolsConfiguration toolsConfig = config.toolsConfig(tools);
        assertNotNull(toolsConfig);
        assertNull(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS));
    }

    @Test
    void meetingMinutesToolsAdapterBean_withoutObservability_createsPlainAdapter() {
        RagToolsBeanConfiguration config = new RagToolsBeanConfiguration();
        RagToolsConfiguration toolsConfig = new RagToolsConfiguration(Map.of());
        QueryAnalyser queryAnalyser = mock(QueryAnalyser.class);
        MeetingMinutesToolsAdapter adapter = config.meetingMinutesToolsAdapter(toolsConfig, queryAnalyser, null);
        assertNotNull(adapter);
        assertFalse(adapter instanceof TracedMeetingMinutesToolsAdapter);
    }

    @Test
    void meetingMinutesToolsAdapterBean_withObservability_createsTracedAdapter() {
        RagToolsBeanConfiguration config = new RagToolsBeanConfiguration();
        RagToolsConfiguration toolsConfig = new RagToolsConfiguration(Map.of());
        QueryAnalyser queryAnalyser = mock(QueryAnalyser.class);
        ObservabilitySupport observability = mock(ObservabilitySupport.class);
        MeetingMinutesToolsAdapter adapter = config.meetingMinutesToolsAdapter(toolsConfig, queryAnalyser, observability);
        assertNotNull(adapter);
        assertInstanceOf(TracedMeetingMinutesToolsAdapter.class, adapter);
    }

    @Test
    void toolRagServiceBean_createsService() {
        RagToolsBeanConfiguration config = new RagToolsBeanConfiguration();
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ToolRagService service = config.toolRagService(embeddingModel, 5);
        assertNotNull(service);
    }
}
