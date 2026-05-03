package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.interfaces.rest.dto.tracereplay.RuntimeTraceReplayResponseDto;
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
 * P22: owner-scoped GET surfaces for runtime trace replay. Delegates only to {@link RuntimeTraceReplayService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayController {

    private final RuntimeTraceReplayService runtimeTraceReplayService;

    public RuntimeTraceReplayController(RuntimeTraceReplayService runtimeTraceReplayService) {
        this.runtimeTraceReplayService = runtimeTraceReplayService;
    }

    @GetMapping("/runtime-traces/{traceId}/replay")
    public ResponseEntity<RuntimeTraceReplayResponseDto> replayByTraceId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID traceId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        RuntimeTraceReplayRequest replayRequest = RuntimeTraceReplayRequest.byTraceId(principal.userId(), traceId);
        var result = runtimeTraceReplayService.replay(replayRequest);
        return ResponseEntity.ok(RuntimeTraceReplayResponseDto.fromReplayHttp(result, replayRequest, traceId, null, null));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/runtime-trace/replay")
    public ResponseEntity<RuntimeTraceReplayResponseDto> replayByMessageId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        RuntimeTraceReplayRequest replayRequest =
                RuntimeTraceReplayRequest.byMessageId(principal.userId(), conversationId, messageId);
        var result = runtimeTraceReplayService.replay(replayRequest);
        return ResponseEntity.ok(
                RuntimeTraceReplayResponseDto.fromReplayHttp(result, replayRequest, null, conversationId, messageId));
    }
}
