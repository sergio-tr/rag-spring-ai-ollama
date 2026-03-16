package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessQueryServiceTest {

    private RagFeatureConfiguration featureConfig;
    private RagToolsConfiguration toolsConfig;
    private QueryExpander expander;
    private QueryAnalyser analyser;
    private NERQueryEnricher nerEnricher;
    private QueryClassifier classifier;
    private ContextRetriever retriever;
    private ChatClient chatClient;
    private DateExistenceGuard dateExistenceGuard;
    private MeetingMinutesToolsAdapter toolsAdapter;
    private ReasoningStrategy reasoningStrategy;
    private ResponseRanker responseRanker;
    private PostRetrievalProcessor postRetrievalProcessor;
    private com.uniovi.rag.tool.ToolRagService toolRagService;
    private ResponseValidator responseValidator;
    private ProcessQueryService service;

    @BeforeEach
    void setUp() {
        featureConfig = new RagFeatureConfiguration();
        featureConfig.setToolsEnabled(false);
        featureConfig.setExpansionEnabled(false);
        featureConfig.setNerEnabled(false);
        featureConfig.setReasoningEnabled(false);
        featureConfig.setUseRetrieval(true);
        toolsConfig = mock(RagToolsConfiguration.class);
        expander = mock(QueryExpander.class);
        analyser = mock(QueryAnalyser.class);
        nerEnricher = mock(NERQueryEnricher.class);
        classifier = mock(QueryClassifier.class);
        retriever = mock(ContextRetriever.class);
        chatClient = mock(ChatClient.class);
        dateExistenceGuard = mock(DateExistenceGuard.class);
        toolsAdapter = mock(MeetingMinutesToolsAdapter.class);
        reasoningStrategy = mock(ReasoningStrategy.class);
        responseRanker = mock(ResponseRanker.class);
        postRetrievalProcessor = mock(PostRetrievalProcessor.class);
        toolRagService = mock(com.uniovi.rag.tool.ToolRagService.class);
        responseValidator = mock(ResponseValidator.class);

        when(expander.expand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(analyser.analyse(anyString())).thenReturn(null);
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("ctx", Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("context");
        when(responseValidator.validateAndClean(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        var callSpec = mock(org.springframework.ai.chat.client.CallResponseSpec.class);
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(callSpec);
        when(callSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Answer from LLM");

        service = new ProcessQueryService(
                featureConfig,
                toolsConfig,
                expander,
                analyser,
                nerEnricher,
                classifier,
                retriever,
                chatClient,
                dateExistenceGuard,
                toolsAdapter,
                reasoningStrategy,
                responseRanker,
                postRetrievalProcessor,
                toolRagService,
                responseValidator,
                null
        );
    }

    @Test
    void generateResponse_toolsDisabled_usesLlmPath() {
        QueryResponse response = service.generateResponse("question");
        assertNotNull(response);
        assertNotNull(response.getAnswer());
    }

    @Test
    void generateResponse_toolsEnabled_classifierReturnsNull_usesLlmPath() {
        featureConfig.setToolsEnabled(true);
        when(classifier.classify(anyString())).thenReturn(null);
        QueryResponse response = service.generateResponse("q");
        assertNotNull(response);
    }

    @Test
    void generateResponse_toolsEnabled_toolReturnsResult_returnsFromTool() {
        featureConfig.setToolsEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.COUNT_DOCUMENTS);
        when(toolsAdapter.execute(any(), anyString())).thenReturn(null); // force tryToolRoute path
        Tool mockTool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.from("5 documents", com.uniovi.rag.tool.CountDocumentsTool.class));

        QueryResponse response = service.generateResponse("how many?");

        assertNotNull(response);
        assertTrue(response.isUsedTool());
        assertEquals("5 documents", response.getAnswer());
    }
}
