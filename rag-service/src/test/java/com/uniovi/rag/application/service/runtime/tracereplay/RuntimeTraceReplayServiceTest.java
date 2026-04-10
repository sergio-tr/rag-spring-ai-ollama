package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeTraceReplayServiceTest {

    @Test
    void delegates_trace_read_to_query_service_only() {
        UUID userId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        RuntimeTraceQueryService query = mock(RuntimeTraceQueryService.class);
        RuntimeTraceReplayEligibilityResolver eligibility = mock(RuntimeTraceReplayEligibilityResolver.class);
        RuntimeTraceReplayInputLoader loader = mock(RuntimeTraceReplayInputLoader.class);
        RuntimeTraceReplayStrategy strategy = mock(RuntimeTraceReplayStrategy.class);

        RuntimeExecutionTraceDetailDto detail = sampleDetail(userId);
        when(query.getTraceDetailById(userId, traceId)).thenReturn(detail);

        when(eligibility.resolve(detail))
                .thenReturn(
                        new RuntimeTraceReplayEligibilityResolver.RuntimeTraceReplayEligibility(
                                com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayDecision.ok(),
                                Optional.of(
                                        new PinnedReplayExecutionSpec(
                                                DIRECT_WORKFLOW_ROUTE, "DirectLlmWorkflow", List.of(), ""))));

        when(loader.load(eq(userId), eq(detail)))
                .thenReturn(
                        Optional.of(
                                new RuntimeTraceReplayInputLoader.ReplayLoadedInputs(
                                        null, null, null, List.of(), List.of())));

        when(strategy.execute(any(), any(), any()))
                .thenReturn(RuntimeTraceReplayResult.success("ok", com.uniovi.rag.domain.runtime.engine.ExecutionTrace.placeholder()));

        RuntimeTraceReplayService service =
                new RuntimeTraceReplayService(query, eligibility, loader, strategy);

        RuntimeTraceReplayResult out =
                service.replay(RuntimeTraceReplayRequest.byTraceId(userId, traceId));

        assertThat(out.outcome()).isEqualTo(RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED);
        verify(query, Mockito.times(1)).getTraceDetailById(userId, traceId);
    }

    private static RuntimeExecutionTraceDetailDto sampleDetail(UUID userId) {
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID snap = UUID.randomUUID();
        return new RuntimeExecutionTraceDetailDto(
                UUID.randomUUID(),
                Instant.now(),
                userId,
                pid,
                cid,
                mid,
                "c",
                snap,
                "h",
                "DirectLlmWorkflow",
                true,
                "OK",
                true,
                "OK",
                DIRECT_WORKFLOW_ROUTE.name(),
                false,
                false,
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                "NOT_NEEDED",
                RuntimeExecutionTraceEntityMapper.TRACE_SCHEMA_VERSION,
                Map.of(),
                List.of());
    }
}
