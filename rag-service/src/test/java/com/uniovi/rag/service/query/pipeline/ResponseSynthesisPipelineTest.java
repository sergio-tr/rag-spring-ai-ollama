package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.application.model.DraftAndContext;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.model.ReasoningPreOutput;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseSynthesisPipelineTest {

    @Mock
    private RagFeatureConfiguration featureConfig;

    @Mock
    private DateExistenceGuard dateExistenceGuard;

    @Mock
    private ToolRoutingService toolRouting;

    @Mock
    private AnswerGenerationKernel kernel;

    @InjectMocks
    private ResponseSynthesisPipeline pipeline;

    @Test
    void synthesizeCore_usesLlmFallback_whenNoGuardOrTool() {
        when(featureConfig.isMetadataEnabled()).thenReturn(false);
        when(toolRouting.tryPreferToolForDate(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(toolRouting.tryMainToolsBlock(any(), any())).thenReturn(java.util.Optional.empty());
        when(toolRouting.tryToolRoute(any(), any(), any())).thenReturn(null);
        when(kernel.askModel(eq("q"), isNull(), eq(QueryType.FIND_PARAGRAPH))).thenReturn("final");

        PreparedQuery pq = new PreparedQuery("q", null, QueryType.FIND_PARAGRAPH);
        CoreSynthesisResult r = pipeline.synthesizeCore(pq, null);

        assertThat(r.kind()).isEqualTo(CoreSynthesisResult.Kind.LLM);
        assertThat(r.draftAndContext().draft()).isEqualTo("final");
    }

    @Test
    void synthesizeCore_usesKernelWithPreStep_whenReasoningPrePresent() {
        when(featureConfig.isMetadataEnabled()).thenReturn(false);
        when(toolRouting.tryPreferToolForDate(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(toolRouting.tryMainToolsBlock(any(), any())).thenReturn(java.util.Optional.empty());
        when(toolRouting.tryToolRoute(any(), any(), any())).thenReturn(null);
        DraftAndContext d = new DraftAndContext("with-plan", "with-plan");
        when(kernel.askModelWithPreStep(eq("q"), isNull(), eq(QueryType.COUNT_DOCUMENTS), eq("plan")))
                .thenReturn(d);

        PreparedQuery pq = new PreparedQuery("q", null, QueryType.COUNT_DOCUMENTS);
        ReasoningPreOutput pre = ReasoningPreOutput.of("plan");
        CoreSynthesisResult r = pipeline.synthesizeCore(pq, pre);

        assertThat(r.kind()).isEqualTo(CoreSynthesisResult.Kind.LLM);
        assertThat(r.draftAndContext().draft()).isEqualTo("with-plan");
    }

    @Test
    void synthesizeCore_treatsNullModelAnswer_asEmptyDraft() {
        when(featureConfig.isMetadataEnabled()).thenReturn(false);
        when(toolRouting.tryPreferToolForDate(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(toolRouting.tryMainToolsBlock(any(), any())).thenReturn(java.util.Optional.empty());
        when(toolRouting.tryToolRoute(any(), any(), any())).thenReturn(null);
        when(kernel.askModel(eq("q"), any(JSONObject.class), isNull())).thenReturn(null);

        PreparedQuery pq = new PreparedQuery("q", new JSONObject(), null);
        CoreSynthesisResult r = pipeline.synthesizeCore(pq, null);

        assertThat(r.draftAndContext().draft()).isEmpty();
    }

    @Test
    void fallbackPlainLlm_delegatesToKernel() {
        when(kernel.askModel(eq("x"), isNull(), eq(QueryType.GET_FIELD))).thenReturn("only-llm");

        String out = pipeline.fallbackPlainLlm(new PreparedQuery("x", null, QueryType.GET_FIELD));

        assertThat(out).isEqualTo("only-llm");
    }
}
