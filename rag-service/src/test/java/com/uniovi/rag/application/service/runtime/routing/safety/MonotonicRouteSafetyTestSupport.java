package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.reasoning.StructuredAnswerPlanService;
import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.RouteExecutionGateBuilder;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.List;
import java.util.Optional;

import org.mockito.stubbing.Answer;

public final class MonotonicRouteSafetyTestSupport {

    private MonotonicRouteSafetyTestSupport() {}

    public static DeterministicToolRoutingStrategy deterministicToolRoutingStrategy() {
        return new DeterministicToolRoutingStrategy(
                new DeterministicToolRoutingPolicy(), new RouteExecutionGateBuilder());
    }

    public static FunctionCallingRoutingStrategy functionCallingRoutingStrategy() {
        return new FunctionCallingRoutingStrategy(
                new FunctionCallingRoutingPolicy(), new RouteExecutionGateBuilder());
    }

    public static AdvisorRoutingStrategy advisorRoutingStrategy() {
        return new AdvisorRoutingStrategy(new AdvisorRoutingPolicy(), new RouteExecutionGateBuilder());
    }

    public static StructuredAnswerPlanService structuredAnswerPlanNoOp() {
        StructuredAnswerPlanService service = mock(StructuredAnswerPlanService.class);
        when(service.plan(any(), any()))
                .thenReturn(new StructuredAnswerPlanService.PlanResult(Optional.empty(), List.of()));
        return service;
    }

    public static MonotonicRouteSafetyService permissiveSafety() {
        MonotonicRouteSafetyService service = mock(MonotonicRouteSafetyService.class);
        when(service.validateToolResult(any(QueryPlan.class), any()))
                .thenReturn(RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"));
        when(service.validateFunctionResult(any(QueryPlan.class), any()))
                .thenReturn(RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"));
        when(service.validateRetrievalAnswer(any(QueryPlan.class), any(), anyBoolean()))
                .thenReturn(RouteCandidateValidationResult.accepted(0.85, "TOPIC_COVERED"));
        Answer<Optional<MonotonicRouteSafetyService.CandidateScore>> pickSafe =
                invocation -> {
                    Optional<MonotonicRouteSafetyService.CandidateScore> tool = invocation.getArgument(1);
                    Optional<MonotonicRouteSafetyService.CandidateScore> function = invocation.getArgument(2);
                    MonotonicRouteSafetyService.CandidateScore retrieval = invocation.getArgument(3);
                    boolean advancedRejected =
                            invocation.getArguments().length > 4
                                    && Boolean.TRUE.equals(invocation.getArgument(4));
                    if (advancedRejected) {
                        return Optional.of(retrieval);
                    }
                    return tool.or(() -> function).or(() -> Optional.of(retrieval));
                };
        when(service.selectSafest(any(), any(), any(), any())).thenAnswer(pickSafe);
        when(service.selectSafest(any(), any(), any(), any(), anyBoolean())).thenAnswer(pickSafe);
        return service;
    }
}
