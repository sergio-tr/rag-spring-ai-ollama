package com.uniovi.rag.service.query;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.application.model.PostStepOutput;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.RankerResult;
import com.uniovi.rag.application.model.ReasoningPreOutput;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.NaiveCorpusContextService;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private ResponseValidator responseValidator;
    private OllamaConnectivityChecker ollamaConnectivityChecker;
    private ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    private ModelCatalogPort modelCatalogPort;
    private ProcessQueryService service;

    @BeforeEach
    void setUp() {
        featureConfig = new RagFeatureConfiguration();
        featureConfig.setToolsEnabled(false);
        featureConfig.setExpansionEnabled(false);
        featureConfig.setNerEnabled(false);
        featureConfig.setReasoningEnabled(false);
        featureConfig.setUseRetrieval(true);
        featureConfig.setUseAdvisor(false);
        toolsConfig = mock(RagToolsConfiguration.class);
        expander = mock(QueryExpander.class);
        analyser = mock(QueryAnalyser.class);
        nerEnricher = mock(NERQueryEnricher.class);
        classifier = mock(QueryClassifier.class);
        retriever = mock(ContextRetriever.class);
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("Answer from LLM");
        dateExistenceGuard = mock(DateExistenceGuard.class);
        toolsAdapter = mock(MeetingMinutesToolsAdapter.class);
        reasoningStrategy = mock(ReasoningStrategy.class);
        responseRanker = mock(ResponseRanker.class);
        postRetrievalProcessor = mock(PostRetrievalProcessor.class);
        responseValidator = mock(ResponseValidator.class);
        ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        chatScopedRagConfigResolver = mock(ChatScopedRagConfigResolver.class);
        modelCatalogPort = mock(ModelCatalogPort.class);
        when(modelCatalogPort.allowedLlmNamesInGovernance()).thenReturn(Collections.emptySet());
        when(chatScopedRagConfigResolver.resolveForExecutionContext(any())).thenAnswer(inv -> RagConfig.fromFeatureConfiguration(
                featureConfig, 10, 0.7, "gemma3:4b", "mxbai-embed-large", "default", "SIMPLE"));
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());

        when(expander.expand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(analyser.analyse(anyString())).thenReturn(null);
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("ctx", Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("context");
        when(responseValidator.validateAndClean(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        service = newService(null);
    }

    private ProcessQueryService newService(QuestionAnswerAdvisor advisor) {
        return new ProcessQueryService(
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
                responseValidator,
                advisor,
                ollamaConnectivityChecker,
                mock(NaiveCorpusContextService.class),
                modelCatalogPort,
                chatScopedRagConfigResolver,
                null,
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

    @Test
    void generateResponse_emptyQuery_returnsLlmErrorMessage() {
        QueryResponse r = service.generateResponse("   ");
        assertNotNull(r);
        assertNotNull(r.getAnswer());
    }

    @Test
    void generateResponse_nullQuery_returnsResponse() {
        QueryResponse r = service.generateResponse(null);
        assertNotNull(r);
        assertNotNull(r.getAnswer());
    }

    @Test
    void generateResponse_classifierOverride_booleanPhrase_routesBooleanQuery() {
        featureConfig.setToolsEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.COUNT_DOCUMENTS);
        when(toolsConfig.getTool(QueryType.BOOLEAN_QUERY)).thenReturn(null);

        QueryResponse r = service.generateResponse("confirma si aparece en el acta");
        assertNotNull(r);
    }

    @Test
    void generateResponse_reasoningEnabled_withAcceptableDraft_usesReasoningPath() {
        featureConfig.setToolsEnabled(true);
        featureConfig.setReasoningEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.SUMMARIZE_MEETING);
        when(reasoningStrategy.runPreStep(anyString(), any(), any(), anyString()))
                .thenReturn(ReasoningPreOutput.of("plan"));
        when(toolsAdapter.execute(any(), anyString())).thenReturn(null);
        Tool mockTool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.SUMMARIZE_MEETING)).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(
                ToolResult.from(
                        "This is a long enough draft answer for the meeting summary that passes minimum length.",
                        com.uniovi.rag.tool.CountDocumentsTool.class));

        QueryResponse r = service.generateResponse("summarize meeting");
        assertNotNull(r);
        assertNotNull(r.getAnswer());
        verify(reasoningStrategy).runPreStep(anyString(), any(), any(), anyString());
        verify(reasoningStrategy).runPostStep(anyString(), anyString(), anyString());
    }

    @Test
    void generateResponse_reasoningEnabled_unacceptableDraft_fallsBackToAskModel() {
        featureConfig.setToolsEnabled(true);
        featureConfig.setReasoningEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.SUMMARIZE_MEETING);
        when(reasoningStrategy.runPreStep(anyString(), any(), any(), anyString()))
                .thenReturn(ReasoningPreOutput.of("x"));
        when(toolsAdapter.execute(any(), anyString())).thenReturn(null);
        Tool mockTool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.SUMMARIZE_MEETING)).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(
                ToolResult.from("short", com.uniovi.rag.tool.CountDocumentsTool.class));

        QueryResponse r = service.generateResponse("summarize");
        assertNotNull(r);
        assertNotNull(r.getAnswer());
    }

    @Test
    void generateResponse_reasoningWithRanker_usesRankerChoice() {
        featureConfig.setToolsEnabled(true);
        featureConfig.setReasoningEnabled(true);
        featureConfig.setRankerEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.SUMMARIZE_MEETING);
        when(reasoningStrategy.runPreStep(anyString(), any(), any(), anyString()))
                .thenReturn(ReasoningPreOutput.of("plan"));
        when(reasoningStrategy.runPostStep(anyString(), anyString(), anyString()))
                .thenReturn(PostStepOutput.verified("verified"));
        when(toolsAdapter.execute(any(), anyString())).thenReturn(null);
        Tool mockTool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.SUMMARIZE_MEETING)).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(
                ToolResult.from(
                        "This is a long enough draft answer for the meeting summary that passes minimum length.",
                        com.uniovi.rag.tool.CountDocumentsTool.class));
        when(responseRanker.selectBest(anyString(), anyString(), anyList()))
                .thenReturn(RankerResult.of("ranked best", 0, List.of(1.0, 0.5)));

        QueryResponse r = service.generateResponse("summarize long");
        assertNotNull(r);
        assertTrue(r.getAnswer().contains("ranked best"));
    }

    @Test
    void generateResponse_metadataEnabled_dateGuardReturns_earlyExit() {
        featureConfig.setMetadataEnabled(true);
        featureConfig.setToolsEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.DECISION_EXTRACTION);
        JSONObject ner = new JSONObject();
        ner.put("date", "2025-03-01");
        when(analyser.analyse(anyString())).thenReturn(ner);
        featureConfig.setNerEnabled(true);
        when(dateExistenceGuard.checkNoActaForDate(anyString(), any(), any()))
                .thenReturn(java.util.Optional.of(ToolResult.from("no acta", com.uniovi.rag.tool.CountDocumentsTool.class)));

        QueryResponse r = service.generateResponse("acuerdos del acta");
        assertNotNull(r);
        assertEquals("no acta", r.getAnswer());
    }

    @Test
    void generateResponse_useRetrievalFalse_skipsRetriever() {
        featureConfig.setUseRetrieval(false);
        featureConfig.setToolsEnabled(false);
        QueryResponse r = service.generateResponse("hello");
        assertNotNull(r);
        verify(retriever, never()).retrieve(anyString());
    }

    @Test
    void generateResponse_toolsEnabled_deterministicAdapterPathReturnsAnswer() {
        featureConfig.setToolsEnabled(true);
        when(classifier.classify(anyString())).thenReturn(QueryType.COUNT_DOCUMENTS);
        when(toolsAdapter.execute(eq(QueryType.COUNT_DOCUMENTS), anyString()))
                .thenReturn(ToolResult.from("via adapter", com.uniovi.rag.tool.CountDocumentsTool.class));

        QueryResponse r = service.generateResponse("count docs");
        assertNotNull(r);
        assertTrue(r.isUsedTool());
        assertEquals("via adapter", r.getAnswer());
    }

    @Test
    void generateResponse_connectivityFailure_throwsRagServiceException() {
        when(retriever.retrieve(anyString())).thenThrow(new ResourceAccessException("I/O error", new ConnectException()));
        // Document-scoped query: avoids general-knowledge shortcut that skips retrieval.
        assertThrows(RagServiceException.class, () -> service.generateResponse("acuerdos de la última reunión"));
    }
}
