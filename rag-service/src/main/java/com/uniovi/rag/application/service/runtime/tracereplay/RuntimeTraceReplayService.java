package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Single application-layer owner for P18 runtime trace replay (internal only; no REST in P18).
 */
@Service
public class RuntimeTraceReplayService {

    private final RuntimeTraceQueryService traceQueryService;
    private final RuntimeTraceReplayEligibilityResolver eligibilityResolver;
    private final RuntimeTraceReplayInputLoader inputLoader;
    private final RuntimeTraceReplayStrategy replayStrategy;

    public RuntimeTraceReplayService(
            RuntimeTraceQueryService traceQueryService,
            RuntimeTraceReplayEligibilityResolver eligibilityResolver,
            RuntimeTraceReplayInputLoader inputLoader,
            RuntimeTraceReplayStrategy replayStrategy) {
        this.traceQueryService = traceQueryService;
        this.eligibilityResolver = eligibilityResolver;
        this.inputLoader = inputLoader;
        this.replayStrategy = replayStrategy;
    }

    /**
     * Loads the persisted trace exclusively via {@link RuntimeTraceQueryService} (P16); performs no writes.
     */
    public RuntimeTraceReplayResult replay(RuntimeTraceReplayRequest request) {
        RuntimeExecutionTraceDetailDto trace = loadTrace(request);
        RuntimeTraceReplayEligibilityResolver.RuntimeTraceReplayEligibility eligibility =
                eligibilityResolver.resolve(trace);
        if (!eligibility.decision().eligible()) {
            return RuntimeTraceReplayResult.unsupported(
                    eligibility.decision().unsupportedOutcome().orElse(RuntimeTraceReplayOutcome.NOT_ATTEMPTED),
                    eligibility.decision().reasonDetail());
        }
        PinnedReplayExecutionSpec pin =
                eligibility.pin().orElseThrow(() -> new IllegalStateException("pin missing after eligibility"));

        Optional<RuntimeTraceReplayInputLoader.ReplayLoadedInputs> inputs =
                inputLoader.load(request.userId(), trace);
        if (inputs.isEmpty()) {
            return RuntimeTraceReplayResult.unsupported(
                    RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_RESOLVED_CONFIG_SNAPSHOT_ID,
                    Optional.of("resolved_config_snapshot_unusable"));
        }

        return replayStrategy.execute(trace, inputs.get(), pin);
    }

    private RuntimeExecutionTraceDetailDto loadTrace(RuntimeTraceReplayRequest request) {
        UUID userId = request.userId();
        return switch (request.mode()) {
            case BY_TRACE_ID -> traceQueryService.getTraceDetailById(userId, request.traceId().orElseThrow());
            case BY_MESSAGE_ID ->
                    traceQueryService.getMostRecentTraceDetailByMessageId(
                            userId, request.conversationId().orElseThrow(), request.messageId().orElseThrow());
        };
    }
}
