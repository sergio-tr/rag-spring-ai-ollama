package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.advisor.AdvisorExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.judge.JudgeKind;

import java.util.List;
import java.util.Optional;

/**
 * Collaboration records for {@link RagExecutionOrchestrator}. Extracted to keep the orchestrator
 * readable without changing runtime behaviour.
 */
public final class RagExecutionOrchestrationSupport {

	private RagExecutionOrchestrationSupport() {}

	public record ExecutionOutcome(RagExecutionResult result, ExecutionTrace trace) {}

	public record RoutingSnapshot(
			AdaptiveRouteKind routeKind,
			Optional<AdaptiveRouteKind> fallbackWorkflowRouteKind,
			List<ExecutionStageTrace> routingStages,
			boolean routingAttempted,
			AdaptiveRoutingOutcome routingOutcome,
			boolean fallbackApplied,
			Optional<AdaptiveRouteKind> fallbackAppliedKind,
			boolean workflowSelectorInvoked) {

		public static RoutingSnapshot disabledByConfig(AdaptiveRouteKind compat) {
			return new RoutingSnapshot(
					compat,
					Optional.empty(),
					List.of(),
					false,
					AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
					false,
					Optional.empty(),
					true);
		}

		public static RoutingSnapshot enabled(
				AdaptiveRouteKind kind,
				com.uniovi.rag.domain.runtime.routing.RouteExecutionGate gate,
				List<ExecutionStageTrace> stages) {
			return new RoutingSnapshot(
					kind,
					gate.fallbackRouteKind(),
					List.copyOf(stages),
					true,
					AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED,
					false,
					Optional.empty(),
					false);
		}

		public RoutingSnapshot withOutcome(
				AdaptiveRoutingOutcome outcome,
				boolean fbApplied,
				Optional<AdaptiveRouteKind> fbKind,
				boolean workflowSelectorInvoked) {
			return new RoutingSnapshot(
					routeKind,
					fallbackWorkflowRouteKind,
					routingStages,
					routingAttempted,
					outcome,
					fbApplied,
					fbKind == null ? Optional.empty() : fbKind,
					workflowSelectorInvoked);
		}

		public RoutingSnapshot snapshotForTrace() {
			return this;
		}

		public RoutingSnapshot snapshotForTrace(
				AdaptiveRoutingOutcome outcome,
				boolean fbApplied,
				Optional<AdaptiveRouteKind> fbKind,
				boolean workflowSelectorInvoked) {
			return withOutcome(outcome, fbApplied, fbKind, workflowSelectorInvoked);
		}
	}

	public record JudgeSnapshot(
			boolean judgeAttempted,
			JudgeCandidateSource candidateSource,
			boolean retryRequested,
			boolean retryAttempted,
			boolean retrySucceeded,
			JudgeOutcome finalOutcome,
			boolean finalAnswerFromRetry,
			JudgeKind kind,
			String detail,
			String finalAnswerText,
			List<ExecutionStageTrace> judgeStages) {

		public static JudgeSnapshot notAttempted(JudgeCandidateSource source) {
			return new JudgeSnapshot(
					false,
					source,
					false,
					false,
					false,
					JudgeOutcome.NOT_ATTEMPTED,
					false,
					JudgeKind.POST_ANSWER_JUDGE,
					"",
					"",
					List.of());
		}

		public static JudgeSnapshot fromResult(JudgeCandidateSource source, JudgeExecutionResult r) {
			return new JudgeSnapshot(
					r.judgeAttempted(),
					source,
					r.retryRequested(),
					r.retryAttempted(),
					r.retrySucceeded(),
					r.judgeOutcome(),
					r.finalAnswerFromRetry(),
					JudgeKind.POST_ANSWER_JUDGE,
					"outcome=" + r.judgeOutcome().name(),
					r.finalAnswerText(),
					List.copyOf(r.stageTraces()));
		}
	}

	public record AdvisorPhaseResult(ExecutionContext ctx, AdvisorSnapshot snapshot) {}

	public record AdvisorSnapshot(
			List<ExecutionStageTrace> advisorStages,
			boolean advisorAttempted,
			boolean advisorShortCircuitedContextPrep,
			String advisorKindsExecuted,
			AdvisorOutcome advisorOutcome,
			int packedContextBlockCount,
			int packedContextSourceCount) {

		public static AdvisorSnapshot notReached(AdvisorOutcome outcome) {
			return new AdvisorSnapshot(List.of(), false, false, "", outcome, 0, 0);
		}

		public static AdvisorSnapshot suppressed(List<ExecutionStageTrace> stages) {
			return new AdvisorSnapshot(stages, false, false, "", AdvisorOutcome.SUPPRESSED_BY_POLICY, 0, 0);
		}

		public static AdvisorSnapshot fromExecution(List<ExecutionStageTrace> stages, AdvisorExecutionResult result) {
			Optional<PackedContextSet> packed = result.packedContextSet();
			int blocks = packed.map(PackedContextSet::totalBlockCount).orElse(0);
			int sources = packed.map(PackedContextSet::totalSourceCount).orElse(0);
			boolean shortCirc =
					result.outcome() == AdvisorOutcome.EXECUTED_SUCCESS && result.shortCircuitedContextPrep();
			return new AdvisorSnapshot(
					stages,
					true,
					shortCirc,
					"RETRIEVAL_ADVISOR,CONTEXT_PACKING_ADVISOR",
					result.outcome(),
					blocks,
					sources);
		}
	}

	public record FcGate(
			boolean functionCallingAttempted,
			FunctionCallingOutcome functionCallingOutcome,
			String functionCallingToolKind,
			boolean functionCallingShortCircuited,
			Optional<FunctionCallingExecutionResult> fcResult,
			List<ExecutionStageTrace> stageTraces) {

		public static FcGate blockedByDeterministicFailure() {
			return new FcGate(
					false,
					FunctionCallingOutcome.FC_BLOCKED_BY_DETERMINISTIC_TOOL_FAILURE,
					"",
					false,
					Optional.empty(),
					List.of());
		}

		public static FcGate notAttempted(FunctionCallingOutcome outcome) {
			return new FcGate(false, outcome, "", false, Optional.empty(), List.of());
		}
	}
}
