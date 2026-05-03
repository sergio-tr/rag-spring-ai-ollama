package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.AdvisorSnapshot;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.JudgeSnapshot;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.RoutingSnapshot;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Static helpers for building {@link ExecutionTrace} rows and clarification stage labels for
 * {@link RagExecutionOrchestrator}. Behaviour-preserving extraction.
 */
public final class RagExecutionTraceSupport {

	private static final String QU_TRACE_MESSAGE_PREFIX = "message=";

	private RagExecutionTraceSupport() {}

	public static List<ExecutionStageTrace> buildClarificationPreQuStages(ExecutionContext ctx) {
		return List.of(
				new ExecutionStageTrace(
						"clarification_state_resolve",
						0L,
						ExecutionStageOutcome.SUCCESS,
						clarifyResolveMessage(ctx)),
				new ExecutionStageTrace(
						"clarification_query_refine",
						0L,
						ExecutionStageOutcome.SUCCESS,
						clarifyRefineMessage(ctx)));
	}

	public static String clarifyResolveMessage(ExecutionContext ctx) {
		var disableReason = ctx.clarificationDisableReason();
		if (disableReason.isPresent()) {
			return "disable_reason=" + disableReason.get();
		}
		if (ctx.invalidPendingRecoveredThisTurn()) {
			return "invalid_pending_state_recovered";
		}
		return "pending_valid="
				+ ctx.validPendingExistedAtLoad()
				+ " merged="
				+ ctx.pendingClarificationLoadedForTrace();
	}

	public static String clarifyRefineMessage(ExecutionContext ctx) {
		return "merged_before_qu=" + ctx.pendingClarificationLoadedForTrace();
	}

	public static ExecutionStageTrace clarificationPolicyStage(ClarificationDecision d) {
		return new ExecutionStageTrace(
				"clarification_policy",
				0L,
				ExecutionStageOutcome.SUCCESS,
				"outcome=" + d.terminalOutcome().name() + " " + d.policyTraceNote());
	}

	public static ExecutionTrace assembleTrace(
			ExecutionContext ctx,
			RagExecutionResult partial,
			String workflowName,
			List<ExecutionStageTrace> clarificationStagesBeforeQu,
			List<ExecutionStageTrace> memoryStagesBeforeQu,
			List<ExecutionStageTrace> quStages,
			List<ExecutionStageTrace> clarificationStagesAfterQu,
			List<ExecutionStageTrace> routingStages,
			List<ExecutionStageTrace> toolStages,
			List<ExecutionStageTrace> fcStages,
			DeterministicToolExecutionResult toolResult,
			boolean functionCallingAttempted,
			FunctionCallingOutcome functionCallingOutcome,
			String functionCallingToolKind,
			boolean functionCallingShortCircuited,
			AdvisorSnapshot advisor,
			JudgeSnapshot judge,
			RoutingSnapshot routing,
			ClarificationDecision clarificationDecision) {
		List<ExecutionStageTrace> all = new ArrayList<>();
		all.addAll(clarificationStagesBeforeQu);
		all.addAll(memoryStagesBeforeQu);
		all.addAll(quStages);
		all.addAll(clarificationStagesAfterQu);
		all.addAll(routingStages);
		all.addAll(toolStages);
		all.addAll(fcStages);
		all.addAll(advisor.advisorStages());
		all.addAll(partial.workflowStageTraces());
		all.addAll(judge.judgeStages());
		QueryPlan qp = ctx.queryPlan().orElse(null);
		String toolOutcome = toolResult.outcome().name();
		String toolKind = toolResult.toolKind().map(Enum::name).orElse("");
		String toolDetail = buildToolDetail(toolResult);
		boolean pendingConsumed = ctx.pendingClarificationLoadedForTrace();
		boolean questionAsked = clarificationDecision.ask();
		return new ExecutionTrace(
				List.copyOf(all),
				workflowName,
				partial.retrievalUsed(),
				partial.metadataUsed(),
				partial.usedKnowledgeSnapshotIds(),
				partial.usedResolvedConfigSnapshotId(),
				partial.usedConfigHash(),
				qp != null ? qp.queryPlanVersion() : "",
				qp != null ? qp.classifierStatus().name() : "",
				qp != null ? qp.classifierLabel() : "",
				qp != null ? qp.expectedAnswerShape().name() : "",
				qp != null ? qp.ambiguityAssessment().status().name() : "",
				ctx.resolved().compatibility().severity().name(),
				ctx.memoryAttempted(),
				ctx.memoryOutcome().name(),
				ctx.memoryHistoryLoaded(),
				ctx.memoryCondensationAttempted(),
				ctx.memoryCondensationUsed(),
				ctx.memoryFallbackApplied(),
				routing.routingAttempted(),
				routing.routingOutcome().name(),
				routing.routeKind().name(),
				routing.fallbackApplied(),
				routing.fallbackAppliedKind().map(Enum::name).orElse(""),
				routing.workflowSelectorInvoked(),
				toolOutcome,
				toolKind,
				toolDetail,
				functionCallingAttempted,
				functionCallingOutcome.name(),
				functionCallingToolKind != null ? functionCallingToolKind : "",
				functionCallingShortCircuited,
				partial.retrievalDiagnostics(),
				advisor.advisorAttempted(),
				advisor.advisorShortCircuitedContextPrep(),
				advisor.advisorKindsExecuted(),
				advisor.advisorOutcome().name(),
				advisor.packedContextBlockCount(),
				advisor.packedContextSourceCount(),
				judge.judgeAttempted(),
				judge.candidateSource().name(),
				judge.retryRequested(),
				judge.retryAttempted(),
				judge.retrySucceeded(),
				judge.finalOutcome().name(),
				judge.finalAnswerFromRetry(),
				judge.kind().name(),
				judge.detail(),
				true,
				clarificationDecision.terminalOutcome().name(),
				pendingConsumed,
				questionAsked);
	}

	public static String buildToolDetail(DeterministicToolExecutionResult toolResult) {
		String notes = toolResult.traceNotes().stream().collect(Collectors.joining(";"));
		if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_FAILED_INFRA) {
			return "tool_fallback_to_workflow;" + notes;
		}
		return notes;
	}

	public static List<ExecutionStageTrace> projectDeterministicToolStages(DeterministicToolExecutionResult r) {
		List<ExecutionStageTrace> out = new ArrayList<>();
		String msgBase = "outcome=" + r.outcome() + " success=" + r.success();
		String notes = String.join(" | ", r.traceNotes());
		out.add(
				new ExecutionStageTrace(
						"tool_resolve",
						0L,
						ExecutionStageOutcome.SUCCESS,
						msgBase + " notes=" + notes));

		ExecutionStageOutcome execOutcome;
		if (r.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS) {
			execOutcome = ExecutionStageOutcome.SUCCESS;
		} else if (r.outcome() == DeterministicToolOutcome.EXECUTED_FAILED_INFRA) {
			execOutcome = ExecutionStageOutcome.FAILED;
		} else {
			execOutcome = ExecutionStageOutcome.SKIPPED;
		}
		out.add(new ExecutionStageTrace("tool_execute", 0L, execOutcome, msgBase));

		ExecutionStageOutcome mapOutcome =
				r.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS
						? ExecutionStageOutcome.SUCCESS
						: ExecutionStageOutcome.SKIPPED;
		out.add(new ExecutionStageTrace("tool_result_map", 0L, mapOutcome, msgBase));
		return out;
	}

	public static List<ExecutionStageTrace> projectQuStages(QueryPlan plan) {
		List<ExecutionStageTrace> out = new ArrayList<>();
		for (String line : plan.pipelineNotes()) {
			ExecutionStageTrace st = parseStageTraceLine(line);
			if (st != null && isFrozenQuStageName(st.stageName())) {
				out.add(st);
			}
		}
		return out;
	}

	private static boolean isFrozenQuStageName(String name) {
		return "qu_normalize".equals(name)
				|| "qu_classify".equals(name)
				|| "qu_extract_entities".equals(name)
				|| "qu_rewrite".equals(name)
				|| "qu_resolve_intent".equals(name)
				|| "qu_resolve_answer_shape".equals(name)
				|| "qu_assess_ambiguity".equals(name);
	}

	private static ExecutionStageTrace parseStageTraceLine(String line) {
		if (line == null || line.isBlank()) {
			return null;
		}
		String[] parts = line.split("\\s+");
		if (parts.length < 3) {
			return null;
		}
		String stageName = parts[0].trim();
		String quStatus = extractToken(line, "qu_status=");
		String durationRaw = extractToken(line, "durationMs=");
		long durationMs = 0;
		try {
			durationMs = Long.parseLong(durationRaw);
		} catch (Exception ignored) {
			durationMs = 0;
		}
		ExecutionStageOutcome outcome =
				switch (quStatus) {
					case "OK", "FALLBACK" -> ExecutionStageOutcome.SUCCESS;
					case "DISABLED" -> ExecutionStageOutcome.SKIPPED;
					case "ERROR" -> ExecutionStageOutcome.FAILED;
					default -> ExecutionStageOutcome.SUCCESS;
				};
		return new ExecutionStageTrace(
				stageName, durationMs, outcome, "qu_status=" + quStatus + " " + extractMessage(line));
	}

	private static String extractToken(String line, String key) {
		int idx = line.indexOf(key);
		if (idx < 0) {
			return "";
		}
		int start = idx + key.length();
		int end = line.indexOf(' ', start);
		return end < 0 ? line.substring(start).trim() : line.substring(start, end).trim();
	}

	private static String extractMessage(String line) {
		int idx = line.indexOf(QU_TRACE_MESSAGE_PREFIX);
		if (idx < 0) {
			return "";
		}
		return QU_TRACE_MESSAGE_PREFIX + line.substring(idx + QU_TRACE_MESSAGE_PREFIX.length()).trim();
	}
}
