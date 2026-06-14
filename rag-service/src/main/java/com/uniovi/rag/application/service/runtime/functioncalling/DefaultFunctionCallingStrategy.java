package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceEvaluator;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalMode;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DefaultFunctionCallingStrategy implements FunctionCallingStrategy {

    private final BackendFunctionCallProposer backendProposer;
    private final BackendControlledFunctionCallingExecutor backendExecutor;
    private final FunctionCallingExecutor nativeProviderExecutor;

    public DefaultFunctionCallingStrategy(
            BackendFunctionCallProposer backendProposer,
            BackendControlledFunctionCallingExecutor backendExecutor,
            FunctionCallingExecutor nativeProviderExecutor) {
        this.backendProposer = backendProposer;
        this.backendExecutor = backendExecutor;
        this.nativeProviderExecutor = nativeProviderExecutor;
    }

    @Override
    public FunctionCallingExecutionResult tryExecute(
            ExecutionContext ctx, QueryPlan plan, FunctionCallingDecision decision) {
        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(
                new ExecutionStageTrace(
                        "function_calling_policy",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "exposed=" + decision.exposedToolKinds()));

        FunctionCallingExecutionSettings.Settings settings = FunctionCallingExecutionSettings.from(ctx);
        if (settings.backendProposalEnabled()) {
            FunctionCallProposal proposal = backendProposer.propose(plan, decision);
            if (proposal.argumentsValid() && proposal.hasToolKind()) {
                return mergeStages(stages, backendExecutor.run(ctx, plan, decision, proposal));
            }
            if (!settings.nativeProviderEnabled()) {
                if (proposal.proposalMode() == FunctionProposalMode.NONE) {
                    return mergeStages(
                            stages,
                            notApplicableResult(proposal, settings.backendProposalEnabled(), false));
                }
                return mergeStages(stages, backendExecutor.run(ctx, plan, decision, proposal));
            }
        }

        if (settings.nativeProviderEnabled()) {
            return mergeNativeStages(stages, nativeProviderExecutor.run(ctx, plan, decision));
        }

        FunctionCallProposal none = FunctionCallProposal.none("backend_proposal_unavailable");
        return mergeStages(stages, notApplicableResult(none, settings.backendProposalEnabled(), false));
    }

    private static FunctionCallingExecutionResult notApplicableResult(
            FunctionCallProposal proposal, boolean backendAttempted, boolean nativeAttempted) {
        return new FunctionCallingExecutionResult(
                FunctionCallingOutcome.NOT_APPLICABLE,
                false,
                proposal.toolKind(),
                "",
                java.util.Map.of(),
                List.of("proposal_not_executable"),
                false,
                List.of(BackendControlledFunctionCallingExecutor.proposalStage(proposal, backendAttempted)),
                Optional.of(proposal),
                backendAttempted,
                nativeAttempted);
    }

    private static FunctionCallingExecutionResult mergeStages(
            List<ExecutionStageTrace> prefix, FunctionCallingExecutionResult inner) {
        List<ExecutionStageTrace> stages = new ArrayList<>(prefix);
        stages.addAll(inner.stageTraces());
        return new FunctionCallingExecutionResult(
                inner.outcome(),
                inner.success(),
                inner.selectedToolKind(),
                inner.answerText(),
                inner.normalizedPayload(),
                inner.traceNotes(),
                inner.shortCircuited(),
                stages,
                inner.proposal(),
                inner.backendFunctionCallAttempted(),
                inner.nativeProviderFunctionCallAttempted());
    }

    private static FunctionCallingExecutionResult mergeNativeStages(
            List<ExecutionStageTrace> prefix, FunctionCallingExecutionResult inner) {
        FunctionCallingExecutionResult merged = mergeStages(prefix, inner);
        return new FunctionCallingExecutionResult(
                merged.outcome(),
                merged.success(),
                merged.selectedToolKind(),
                merged.answerText(),
                merged.normalizedPayload(),
                merged.traceNotes(),
                merged.shortCircuited(),
                merged.stageTraces(),
                merged.proposal(),
                merged.backendFunctionCallAttempted(),
                true);
    }
}
