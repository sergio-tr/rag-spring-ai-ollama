package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalSource;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultFunctionCallingStrategyTest {

    @Test
    void tryExecute_prependsPolicyStageTrace_andKeepsInnerTraces() {
        BackendFunctionCallProposer proposer = mock(BackendFunctionCallProposer.class);
        BackendControlledFunctionCallingExecutor backendExecutor =
                mock(BackendControlledFunctionCallingExecutor.class);
        FunctionCallingExecutor nativeExecutor = mock(FunctionCallingExecutor.class);
        DefaultFunctionCallingStrategy s =
                new DefaultFunctionCallingStrategy(proposer, backendExecutor, nativeExecutor);

        ExecutionContext ctx = mock(ExecutionContext.class);
        RagFeatureConfiguration features = new RagFeatureConfiguration();
        features.setFunctionCallingEnabled(true);
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(features, 12, 0.6, "llm", "emb", "cls", "simple");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        when(ctx.resolved()).thenReturn(resolved);
        QueryPlan plan = mock(QueryPlan.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.EXECUTED_SUCCESS,
                        true,
                        List.of(DeterministicToolKind.GET_FIELD_TOOL),
                        List.of("r"),
                        Optional.empty(),
                        "q",
                        Map.of());

        FunctionCallProposal proposal =
                new FunctionCallProposal(
                        FunctionProposalMode.BACKEND_DETERMINISTIC,
                        "getField",
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        "{\"query\":\"q\",\"field\":\"f\"}",
                        true,
                        Optional.empty(),
                        false,
                        false,
                        Optional.of(1.0),
                        Optional.empty(),
                        FunctionProposalSource.QUERY_SHAPE);

        FunctionCallingExecutionResult inner =
                new FunctionCallingExecutionResult(
                        FunctionCallingOutcome.EXECUTED_SUCCESS,
                        true,
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        "ans",
                        Map.of("k", "v"),
                        List.of("n"),
                        true,
                        List.of(new ExecutionStageTrace("inner", 1, null, "")),
                        Optional.of(proposal),
                        true,
                        false);

        when(proposer.propose(plan, decision)).thenReturn(proposal);
        when(backendExecutor.run(ctx, plan, decision, proposal)).thenReturn(inner);

        FunctionCallingExecutionResult out = s.tryExecute(ctx, plan, decision);

        assertThat(out.stageTraces()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(out.stageTraces().getFirst().stageName()).isEqualTo("function_calling_policy");
        assertThat(out.answerText()).isEqualTo("ans");
        assertThat(out.normalizedPayload()).containsEntry("k", "v");
        assertThat(out.backendFunctionCallAttempted()).isTrue();
    }
}

