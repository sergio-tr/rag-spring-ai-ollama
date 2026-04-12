package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.application.model.DraftAndContext;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.NaiveCorpusContextService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        lenient().when(requestSpec.advisors(any(QuestionAnswerAdvisor.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("This is a valid synthetic answer for testing.");
        lenient().when(responseValidator.validateAndClean(anyString(), anyString()))
                .thenReturn("This is a valid synthetic answer for testing.");
    }

    @Test
    void askModel_withoutRetrieval_returnsValidatedLlmOutput() {
        when(featureConfig.isUseRetrieval()).thenReturn(false);

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                mock(ContextRetriever.class),
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        String out = kernel.askModel("What is 2+2?", new JSONObject(), QueryType.BOOLEAN_QUERY);
        assertEquals("This is a valid synthetic answer for testing.", out);
    }

    @Test
    void askModelWithPreStep_withoutRetrieval_returnsDraft() {
        when(featureConfig.isUseRetrieval()).thenReturn(false);

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                mock(ContextRetriever.class),
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        DraftAndContext draft = kernel.askModelWithPreStep("q", new JSONObject(), QueryType.GET_FIELD, null);
        assertNotNull(draft);
        assertEquals("This is a valid synthetic answer for testing.", draft.draft());
    }

    @Test
    void askModel_generalKnowledgeQuery_skipsRetrievalAndReturnsDirectLlm() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        ContextRetriever retriever = mock(ContextRetriever.class);
        when(callResponseSpec.content()).thenReturn("Ha ha.");
        when(responseValidator.validateAndClean("Ha ha.", "ProcessQueryService-DirectGeneral")).thenReturn("Ha ha.");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        assertEquals("Ha ha.", kernel.askModel("tell me a joke", new JSONObject(), QueryType.BOOLEAN_QUERY));
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void askModelWithPreStep_generalKnowledgeQuery_skipsRetrievalAndReturnsDirectLlm() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        ContextRetriever retriever = mock(ContextRetriever.class);

        when(callResponseSpec.content()).thenReturn("Why did the chicken cross the road?");
        when(responseValidator.validateAndClean(
                "Why did the chicken cross the road?",
                "ProcessQueryService-ReasoningDirect")).thenReturn("Why did the chicken cross the road?");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

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

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

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
        when(responseValidator.validateAndClean("Synthesized answer.", "ProcessQueryService")).thenReturn("Synthesized answer.");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

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

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        DraftAndContext draft = kernel.askModelWithPreStep(
                "acta del pleno", new JSONObject(), QueryType.GET_FIELD, null);
        assertNotNull(draft);
        assertEquals("Answer without real context.", draft.draft());
    }

    @Test
    void generateNoContextResponse_whenLlmReturnsContent_returnsTrimmed() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                mock(ContextRetriever.class),
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        when(callResponseSpec.content()).thenReturn("  trimmed body  ");

        assertEquals("trimmed body", kernel.generateNoContextResponse("hello"));
    }

    @Test
    void generateNoContextResponse_whenLlmThrows_returnsDefaultFallback() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("llm unavailable"));

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                mock(ContextRetriever.class),
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        assertEquals("I could not generate a response. Please try again.", kernel.generateNoContextResponse("hello"));
    }

    @Test
    void askModel_advisorFastPath_returnsAdvisorValidatedAnswer() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isUseAdvisor()).thenReturn(true);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);

        QuestionAnswerAdvisor advisor = mock(QuestionAnswerAdvisor.class);
        ContextRetriever retriever = mock(ContextRetriever.class);
        NaiveCorpusContextService naive = mock(NaiveCorpusContextService.class);
        when(naive.buildNaiveCorpusContextIfConfigured()).thenReturn(null);

        when(callResponseSpec.content()).thenReturn("From advisor");
        when(responseValidator.validateAndClean("From advisor", "ProcessQueryService-Advisor")).thenReturn("From advisor");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                advisor,
                                chatRequestSpecFactory,
                                naive,
                                false));

        assertEquals(
                "From advisor",
                kernel.askModel("What is the capital of France?", new JSONObject(), QueryType.GET_FIELD));
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void askModel_manualRetrieval_whenAdvisorDisabled_usesRetrievedContext() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isUseAdvisor()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("chunk")));
        when(retriever.createContext(any(), anyString(), any()))
                .thenReturn("0123456789".repeat(8));

        when(callResponseSpec.content()).thenReturn("RAG body");
        when(responseValidator.validateAndClean("RAG body", "ProcessQueryService")).thenReturn("RAG body");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        assertEquals(
                "RAG body",
                kernel.askModel("quantum physics overview without acta", new JSONObject(), QueryType.GET_FIELD));
    }

    @Test
    void askModel_manualRetrieval_nerEnabled_passesEnrichedQueryToRetriever() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isUseAdvisor()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(true);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);

        NERQueryEnricher ner = mock(NERQueryEnricher.class);
        when(ner.buildEnrichedQueryForRetrieval(anyString(), any())).thenReturn("ENRICHED acta query");

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(eq("ENRICHED acta query"))).thenReturn(List.of(new Document("chunk")));
        when(retriever.createContext(any(), anyString(), any()))
                .thenReturn("0123456789".repeat(8));

        when(callResponseSpec.content()).thenReturn("RAG ner");
        when(responseValidator.validateAndClean("RAG ner", "ProcessQueryService")).thenReturn("RAG ner");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                ner,
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        JSONObject nerEntities = new JSONObject();
        nerEntities.put("PLACE", "Oviedo");

        assertEquals(
                "RAG ner",
                kernel.askModel("pleno municipal acta del 2024", nerEntities, QueryType.GET_FIELD));
        verify(ner).buildEnrichedQueryForRetrieval(eq("pleno municipal acta del 2024"), eq(nerEntities));
        verify(retriever).retrieve("ENRICHED acta query");
    }

    @Test
    void askModel_postRetrievalEnabled_runsPostProcessorBeforeContext() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isUseAdvisor()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(true);

        ContextRetriever retriever = mock(ContextRetriever.class);
        PostRetrievalProcessor post = mock(PostRetrievalProcessor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("a")));
        when(post.process(any(), anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(retriever.createContext(any(), anyString(), any()))
                .thenReturn("0123456789".repeat(8));

        when(callResponseSpec.content()).thenReturn("out");
        when(responseValidator.validateAndClean("out", "ProcessQueryService")).thenReturn("out");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                post,
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        assertEquals("out", kernel.askModel("topic without acta keywords", new JSONObject(), QueryType.GET_FIELD));
        verify(post).process(any(), anyString());
    }

    @Test
    void askModel_naiveCorpusActive_skipsAdvisorAndUsesConcatenatedChunks() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isUseAdvisor()).thenReturn(true);

        NaiveCorpusContextService naive = mock(NaiveCorpusContextService.class);
        when(naive.buildNaiveCorpusContextIfConfigured()).thenReturn("0123456789".repeat(20));

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(callResponseSpec.content()).thenReturn("naive out");
        when(responseValidator.validateAndClean("naive out", "ProcessQueryService")).thenReturn("naive out");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                naive,
                                false));

        assertEquals(
                "naive out",
                kernel.askModel("quantum field theory primer", new JSONObject(), QueryType.BOOLEAN_QUERY));
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void askModelWithPreStep_naiveCorpus_returnsAnswerWithoutVectorRetrieval() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);

        NaiveCorpusContextService naive = mock(NaiveCorpusContextService.class);
        when(naive.buildNaiveCorpusContextIfConfigured()).thenReturn("0123456789".repeat(15));

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(callResponseSpec.content()).thenReturn("step naive");
        when(responseValidator.validateAndClean("step naive", "ProcessQueryService")).thenReturn("step naive");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                naive,
                                false));

        DraftAndContext draft =
                kernel.askModelWithPreStep(
                        "relativity basics", new JSONObject(), QueryType.GET_FIELD, null);
        assertEquals("step naive", draft.draft());
        assertEquals("0123456789".repeat(15), draft.context());
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void askModelWithPreStep_naiveCorpusEmpty_returnsNoContextDraft() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isNerEnabled()).thenReturn(false);

        NaiveCorpusContextService naive = mock(NaiveCorpusContextService.class);
        when(naive.buildNaiveCorpusContextIfConfigured()).thenReturn("");

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(callResponseSpec.content()).thenReturn("fallback body");
        when(responseValidator.validateAndClean("fallback body", "ProcessQueryService-NoContext"))
                .thenReturn("fallback body");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                naive,
                                false));

        DraftAndContext draft =
                kernel.askModelWithPreStep("relativity basics", new JSONObject(), QueryType.GET_FIELD, null);
        assertEquals("fallback body", draft.draft());
        verify(retriever, never()).retrieve(any());
    }

    @Test
    void callLlmWithPromptContext_retriesThenReturnsValidOnSecondAttempt() {
        when(featureConfig.isUseRetrieval()).thenReturn(true);
        when(featureConfig.isUseAdvisor()).thenReturn(false);
        when(featureConfig.isNerEnabled()).thenReturn(false);
        when(featureConfig.isPostRetrievalEnabled()).thenReturn(false);

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("x")));
        when(retriever.createContext(any(), anyString(), any()))
                .thenReturn("0123456789".repeat(8));

        when(callResponseSpec.content()).thenReturn("bad", "ok");
        when(responseValidator.validateAndClean("bad", "ProcessQueryService")).thenReturn(null);
        when(responseValidator.validateAndClean("ok", "ProcessQueryService")).thenReturn("ok");

        AnswerGenerationKernel kernel =
                new AnswerGenerationKernel(
                        new AnswerGenerationKernel.Dependencies(
                                featureConfig,
                                mock(NERQueryEnricher.class),
                                retriever,
                                mock(PostRetrievalProcessor.class),
                                responseValidator,
                                mock(QuestionAnswerAdvisor.class),
                                chatRequestSpecFactory,
                                null,
                                false));

        assertEquals(
                "ok",
                kernel.askModel("algebraic topology intro", new JSONObject(), QueryType.GET_FIELD));
    }
}
