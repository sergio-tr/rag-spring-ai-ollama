package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * Shared meeting-minutes business execution for P7 deterministic tools and P9 function calling (§6.10).
 */
@Component
public class MeetingMinutesToolExecutionCore {

    private final RagToolsConfiguration toolsConfiguration;

    public MeetingMinutesToolExecutionCore(RagToolsConfiguration toolsConfiguration) {
        this.toolsConfiguration = toolsConfiguration;
    }

    /**
     * Executes the whitelisted tool using {@link QueryPlan} and {@link ExecutionContext} scope (same semantics as pre-extraction deterministic execution).
     */
    public MeetingMinutesToolRawResult execute(DeterministicToolKind kind, ExecutionContext ctx, QueryPlan plan) {
        QueryType queryType = DeterministicToolKindMappings.toQueryType(kind);
        Tool tool = toolsConfiguration.getTool(queryType);
        if (tool == null) {
            return MeetingMinutesToolRawResult.missingTool(kind);
        }
        ToolExecutionContext tex =
                ToolExecutionContext.of(plan.rewrittenQueryText(), queryType, QueryPlanEntitySupport.nerFromPlan(plan));
        try {
            ToolResult raw = tool.execute(tex);
            return MeetingMinutesToolRawResult.ok(kind, raw);
        } catch (RuntimeException e) {
            return MeetingMinutesToolRawResult.runtimeFailure(
                    kind, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
