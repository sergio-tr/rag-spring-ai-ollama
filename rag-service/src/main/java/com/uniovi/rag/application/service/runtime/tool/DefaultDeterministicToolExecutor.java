package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultDeterministicToolExecutor implements DeterministicToolExecutor {

    private final RagToolsConfiguration toolsConfiguration;
    private final DeterministicToolResultMapper resultMapper;

    public DefaultDeterministicToolExecutor(
            RagToolsConfiguration toolsConfiguration, DeterministicToolResultMapper resultMapper) {
        this.toolsConfiguration = toolsConfiguration;
        this.resultMapper = resultMapper;
    }

    @Override
    public DeterministicToolExecutionResult execute(
            DeterministicToolDecision decision, ExecutionContext ctx, QueryPlan plan) {
        if (!decision.selected() || decision.selectedToolKind().isEmpty()) {
            return DeterministicToolExecutionResult.skipped(
                    decision.outcome(), decision.reasons(), Optional.empty());
        }
        DeterministicToolKind kind = decision.selectedToolKind().get();
        QueryType queryType = DeterministicToolKindMappings.toQueryType(kind);
        Tool tool = toolsConfiguration.getTool(queryType);
        if (tool == null) {
            return new DeterministicToolExecutionResult(
                    Optional.of(kind),
                    DeterministicToolOutcome.EXECUTED_FAILED_INFRA,
                    false,
                    "",
                    Map.of("error", "no_tool_registered_for_query_type", "queryType", queryType.name()),
                    List.of("no_tool_registered", "queryType=" + queryType));
        }
        ToolExecutionContext tex =
                ToolExecutionContext.of(plan.rewrittenQueryText(), queryType, QueryPlanEntitySupport.nerFromPlan(plan));
        try {
            ToolResult raw = tool.execute(tex);
            MappedToolOutput mapped = resultMapper.map(raw, kind);
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
        } catch (RuntimeException e) {
            return new DeterministicToolExecutionResult(
                    Optional.of(kind),
                    DeterministicToolOutcome.EXECUTED_FAILED_INFRA,
                    false,
                    "",
                    Map.of("error", "tool_execution_exception", "message", String.valueOf(e.getMessage())),
                    List.of(
                            "tool_execution_exception",
                            e.getClass().getSimpleName(),
                            String.valueOf(e.getMessage())));
        }
    }
}
