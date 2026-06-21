package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.application.service.runtime.tool.MeetingMinutesToolExecutionCore;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Executes validated backend function proposals through the shared tool core. */
@Component
public class BackendControlledFunctionCallingExecutor {

    private final MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore;
    private final FunctionCallingResultMapper resultMapper;

    public BackendControlledFunctionCallingExecutor(
            MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore,
            FunctionCallingResultMapper resultMapper) {
        this.meetingMinutesToolExecutionCore = meetingMinutesToolExecutionCore;
        this.resultMapper = resultMapper;
    }

    public FunctionCallingExecutionResult run(
            ExecutionContext ctx,
            QueryPlan plan,
            FunctionCallingDecision decision,
            FunctionCallProposal proposal) {
        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(proposalStage(proposal, true));
        if (!proposal.argumentsValid() || proposal.toolKind().isEmpty()) {
            stages.add(fcResultMapStage(FunctionCallingOutcome.INVALID_MODEL_OUTPUT));
            return terminalOutcome(
                    FunctionCallingOutcome.INVALID_MODEL_OUTPUT,
                    proposal,
                    Optional.empty(),
                    stages,
                    List.of("proposal_invalid"),
                    true,
                    false);
        }
        DeterministicToolKind kind = proposal.toolKind().orElseThrow();
        try {
            MeetingMinutesToolRawResult raw = meetingMinutesToolExecutionCore.execute(kind, ctx, plan);
            if (raw.status() != MeetingMinutesToolRawResult.Status.OK) {
                stages.add(
                        new ExecutionStageTrace(
                                "function_calling_tool",
                                0L,
                                ExecutionStageOutcome.FAILED,
                                "tool_status=" + raw.status()));
                stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_FAILED_INFRA));
                return terminalOutcome(
                        FunctionCallingOutcome.EXECUTED_FAILED_INFRA,
                        proposal,
                        Optional.of(kind),
                        stages,
                        List.of("tool_infra:" + raw.status()),
                        true,
                        false);
            }
            stages.add(
                    new ExecutionStageTrace(
                            "function_calling_tool",
                            0L,
                            ExecutionStageOutcome.SUCCESS,
                            "kind=" + kind));
            String stableText = resultMapper.stableAnswerText(raw.raw().orElseThrow(), kind);
            Map<String, Object> payload = resultMapper.normalizedPayload(raw.raw().orElseThrow(), kind);
            if (stableText.isBlank()) {
                stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_FAILED_INFRA));
                return terminalOutcome(
                        FunctionCallingOutcome.EXECUTED_FAILED_INFRA,
                        proposal,
                        Optional.of(kind),
                        stages,
                        List.of("mapping_empty"),
                        true,
                        false);
            }
            stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_SUCCESS));
            return new FunctionCallingExecutionResult(
                    FunctionCallingOutcome.EXECUTED_SUCCESS,
                    true,
                    Optional.of(kind),
                    stableText,
                    payload,
                    List.of("backend_fc_success"),
                    true,
                    stages,
                    Optional.of(proposal),
                    true,
                    false);
        } catch (RuntimeException e) {
            stages.add(fcResultMapStage(FunctionCallingOutcome.EXECUTED_FAILED_INFRA));
            return terminalOutcome(
                    FunctionCallingOutcome.EXECUTED_FAILED_INFRA,
                    proposal,
                    proposal.toolKind(),
                    stages,
                    List.of(e.getClass().getSimpleName(), String.valueOf(e.getMessage())),
                    true,
                    false);
        }
    }

    static ExecutionStageTrace proposalStage(FunctionCallProposal proposal, boolean backendAttempted) {
        StringBuilder msg = new StringBuilder();
        msg.append("functionProposalMode=").append(proposal.proposalMode().name());
        msg.append(";functionProposalSource=").append(proposal.proposalSource().name());
        msg.append(";functionProposalValid=").append(proposal.argumentsValid());
        msg.append(";functionProposalRepairAttempted=").append(proposal.repairAttempted());
        msg.append(";functionProposalRepairSucceeded=").append(proposal.repairSucceeded());
        msg.append(";backendFunctionCallAttempted=").append(backendAttempted);
        msg.append(";nativeProviderFunctionCallAttempted=false");
        if (!proposal.functionName().isBlank()) {
            msg.append(";functionCallName=").append(proposal.functionName());
        }
        proposal.toolKind().ifPresent(k -> msg.append(";functionToolKind=").append(k.name()));
        proposal.proposalEvidenceLevel()
                .ifPresent(level -> msg.append(";proposalEvidenceLevel=").append(level.name()));
        proposal.schemaValidationError()
                .ifPresent(err -> msg.append(";schemaValidationError=").append(err));
        return new ExecutionStageTrace(
                "function_calling_proposal", 0L, ExecutionStageOutcome.SUCCESS, msg.toString());
    }

    private static ExecutionStageTrace fcResultMapStage(FunctionCallingOutcome outcome) {
        return new ExecutionStageTrace(
                "function_calling_result_map",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "outcome=" + outcome);
    }

    private static FunctionCallingExecutionResult terminalOutcome(
            FunctionCallingOutcome outcome,
            FunctionCallProposal proposal,
            Optional<DeterministicToolKind> toolKind,
            List<ExecutionStageTrace> stages,
            List<String> notes,
            boolean backendAttempted,
            boolean nativeAttempted) {
        return new FunctionCallingExecutionResult(
                outcome,
                false,
                toolKind,
                "",
                Map.of(),
                notes,
                false,
                stages,
                Optional.of(proposal),
                backendAttempted,
                nativeAttempted);
    }
}
