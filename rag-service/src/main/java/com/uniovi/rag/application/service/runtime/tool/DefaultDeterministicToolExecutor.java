package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultDeterministicToolExecutor implements DeterministicToolExecutor {

    private final MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore;
    private final DeterministicToolResultMapper resultMapper;

    public DefaultDeterministicToolExecutor(
            MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore,
            DeterministicToolResultMapper resultMapper) {
        this.meetingMinutesToolExecutionCore = meetingMinutesToolExecutionCore;
        this.resultMapper = resultMapper;
    }

    @Override
    public DeterministicToolExecutionResult execute(
            DeterministicToolDecision decision, ExecutionContext ctx, QueryPlan plan) {
        Optional<DeterministicToolKind> kindOpt = decision.selectedToolKind();
        if (!decision.selected() || kindOpt.isEmpty()) {
            return DeterministicToolExecutionResult.skipped(
                    decision.outcome(), decision.reasons(), Optional.empty());
        }
        DeterministicToolKind kind = kindOpt.get();
        QueryType queryType = DeterministicToolKindMappings.toQueryType(kind);
        MeetingMinutesToolRawResult raw = meetingMinutesToolExecutionCore.execute(kind, ctx, plan);
        if (raw.status() == MeetingMinutesToolRawResult.Status.MISSING_TOOL) {
            return new DeterministicToolExecutionResult(
                    Optional.of(kind),
                    DeterministicToolOutcome.EXECUTED_FAILED_INFRA,
                    false,
                    "",
                    Map.of("error", "no_tool_registered_for_query_type", "queryType", queryType.name()),
                    List.of("no_tool_registered", "queryType=" + queryType));
        }
        if (raw.status() == MeetingMinutesToolRawResult.Status.RUNTIME_FAILURE) {
            return new DeterministicToolExecutionResult(
                    Optional.of(kind),
                    DeterministicToolOutcome.EXECUTED_FAILED_INFRA,
                    false,
                    "",
                    Map.of(
                            "error",
                            "tool_execution_exception",
                            "message",
                            raw.errorDetail().orElse("")),
                    List.of(
                            "tool_execution_exception",
                            raw.errorDetail().orElse("")));
        }
        MappedToolOutput mapped = resultMapper.map(raw.raw().orElseThrow(), kind);
        if (mapped == null) {
            return new DeterministicToolExecutionResult(
                    Optional.of(kind),
                    DeterministicToolOutcome.EXECUTED_FAILED_INFRA,
                    false,
                    "",
                    Map.of("error", "tool_output_validation_failed"),
                    List.of("tool_output_validation_failed", "kind=" + kind));
        }
        return new DeterministicToolExecutionResult(
                Optional.of(kind),
                DeterministicToolOutcome.EXECUTED_SUCCESS,
                true,
                mapped.answerText(),
                mapped.normalizedPayload(),
                List.of("executed=" + kind));
    }
}
