package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.interfaces.rest.dto.tracecomparison.RuntimeTraceReplayComparisonResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * P20: owner-scoped GET surfaces for replay-vs-original comparison. Delegates only to
 * {@link RuntimeTraceReplayComparisonService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayComparisonController {

    private final RuntimeTraceReplayComparisonService runtimeTraceReplayComparisonService;

    public RuntimeTraceReplayComparisonController(RuntimeTraceReplayComparisonService runtimeTraceReplayComparisonService) {
        this.runtimeTraceReplayComparisonService = runtimeTraceReplayComparisonService;
    }

    @GetMapping("/runtime-traces/{traceId}/replay-comparison")
    public ResponseEntity<RuntimeTraceReplayComparisonResponseDto> replayComparisonByTraceId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID traceId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        var result =
                runtimeTraceReplayComparisonService.compare(
                        RuntimeTraceReplayComparisonRequest.byTraceId(principal.userId(), traceId));
        if (result.runtimeTraceReplayComparisonOutcome()
                == RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE) {
            throw new NotFoundException("trace not found");
        }
        return ResponseEntity.ok(RuntimeTraceReplayComparisonResponseDto.fromRuntimeTraceReplayComparisonResult(result));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/runtime-trace/replay-comparison")
    public ResponseEntity<RuntimeTraceReplayComparisonResponseDto> replayComparisonByMessageId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        var result =
                runtimeTraceReplayComparisonService.compare(
                        RuntimeTraceReplayComparisonRequest.byMessageId(principal.userId(), conversationId, messageId));
        if (result.runtimeTraceReplayComparisonOutcome()
                == RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE) {
            throw new NotFoundException("trace not found");
        }
        return ResponseEntity.ok(RuntimeTraceReplayComparisonResponseDto.fromRuntimeTraceReplayComparisonResult(result));
    }
}
