package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.DraftAndContext;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link AnswerGenerationKernel} when {@code use-retrieval=false}, to cover the
 * no-RAG LLM branches without Spring context (JaCoCo).
 */
class AnswerGenerationKernelTest {

    private RagFeatureConfiguration featureConfig;
    private ChatRequestSpecFactory chatRequestSpecFactory;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ResponseValidator responseValidator;

    @BeforeEach
    void setUp() {
        featureConfig = mock(RagFeatureConfiguration.class);
        chatRequestSpecFactory = mock(ChatRequestSpecFactory.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        responseValidator = mock(ResponseValidator.class);

        when(chatRequestSpecFactory.spec()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("This is a valid synthetic answer for testing.");
        lenient().when(responseValidator.validateAndClean(anyString(), anyString()))
                .thenReturn("This is a valid synthetic answer for testing.");
    }

    @Test
    void askModel_withoutRetrieval_returnsValidatedLlmOutput() {
        when(featureConfig.isUseRetrieval()).thenReturn(false);

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                mock(ContextRetriever.class),
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        String out = kernel.askModel("What is 2+2?", new JSONObject(), QueryType.BOOLEAN_QUERY);
        assertEquals("This is a valid synthetic answer for testing.", out);
    }

    @Test
    void askModelWithPreStep_withoutRetrieval_returnsDraft() {
        when(featureConfig.isUseRetrieval()).thenReturn(false);

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                mock(ContextRetriever.class),
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        DraftAndContext draft = kernel.askModelWithPreStep("q", new JSONObject(), QueryType.GET_FIELD, null);
        assertNotNull(draft);
        assertEquals("This is a valid synthetic answer for testing.", draft.draft());
    }

    @Test
    void askModel_generalKnowledgeQuery_skipsRetrievalAndReturnsDirectLlm() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        ContextRetriever retriever = mock(ContextRetriever.class);
        when(callResponseSpec.content()).thenReturn("Ha ha.");
        when(responseValidator.validateAndClean(eq("Ha ha."), eq("ProcessQueryService-DirectGeneral"))).thenReturn("Ha ha.");

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                retriever,
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        assertEquals("Ha ha.", kernel.askModel("tell me a joke", new JSONObject(), QueryType.BOOLEAN_QUERY));
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void askModelWithPreStep_generalKnowledgeQuery_skipsRetrievalAndReturnsDirectLlm() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        ContextRetriever retriever = mock(ContextRetriever.class);

        when(callResponseSpec.content()).thenReturn("Why did the chicken cross the road?");
        when(responseValidator.validateAndClean(
                eq("Why did the chicken cross the road?"),
                eq("ProcessQueryService-ReasoningDirect"))).thenReturn("Why did the chicken cross the road?");

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                retriever,
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        DraftAndContext draft = kernel.askModelWithPreStep(
                "tell me a joke", new JSONObject(), QueryType.BOOLEAN_QUERY, null);
        assertEquals("Why did the chicken cross the road?", draft.draft());
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void askModelWithPreStep_retrievalThrows_returnsNull() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(any())).thenThrow(new RuntimeException("vector store down"));

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                retriever,
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        assertNull(kernel.askModelWithPreStep(
                "acta del pleno municipal", new JSONObject(), QueryType.GET_FIELD, null));
    }

    @Test
    void askModelWithPreStep_withPreStepThought_includesReasoningBlockInContext() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);
        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(any())).thenReturn(List.of(new Document("fragment")));
        when(retriever.createContext(any(), anyString(), any())).thenReturn("retrieved context");
        when(callResponseSpec.content()).thenReturn("Synthesized answer.");
        when(responseValidator.validateAndClean(eq("Synthesized answer."), eq("ProcessQueryService"))).thenReturn("Synthesized answer.");

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                retriever,
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        DraftAndContext draft = kernel.askModelWithPreStep(
                "acta del pleno", new JSONObject(), QueryType.GET_FIELD, "reasoning step");
        assertEquals("Synthesized answer.", draft.draft());
        assertEquals("retrieved context", draft.context());
    }

    @Test
    void askModelWithPreStep_blankContext_usesNoContextDraft() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);
        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(any())).thenReturn(List.of(new Document("x")));
        when(retriever.createContext(any(), anyString(), any())).thenReturn("   ");

        when(callResponseSpec.content()).thenReturn("Answer without real context.");

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                retriever,
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        DraftAndContext draft = kernel.askModelWithPreStep(
                "acta del pleno", new JSONObject(), QueryType.GET_FIELD, null);
        assertNotNull(draft);
        assertEquals("Answer without real context.", draft.draft());
    }

    @Test
    void generateNoContextResponse_whenLlmReturnsContent_returnsTrimmed() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);

        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                mock(NERQueryEnricher.class),
                mock(ContextRetriever.class),
                mock(PostRetrievalProcessor.class),
                responseValidator,
                mock(QuestionAnswerAdvisor.class),
                chatRequestSpecFactory);

        when(callResponseSpec.content()).thenReturn("  trimmed body  ");

        assertEquals("trimmed body", kernel.generateNoContextResponse("hello"));
    }
}
