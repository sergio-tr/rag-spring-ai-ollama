package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportArtifact;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportSizeExceededException;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * P21: owner-scoped GET download of a single replay-comparison ZIP. Delegates only to
 * {@link RuntimeTraceReplayComparisonExportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayComparisonExportController {

    private final RuntimeTraceReplayComparisonExportService runtimeTraceReplayComparisonExportService;

    public RuntimeTraceReplayComparisonExportController(
            RuntimeTraceReplayComparisonExportService runtimeTraceReplayComparisonExportService) {
        this.runtimeTraceReplayComparisonExportService = runtimeTraceReplayComparisonExportService;
    }

    @GetMapping("/runtime-traces/{traceId}/replay-comparison/export")
    public ResponseEntity<byte[]> exportReplayComparisonByTraceId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID traceId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RuntimeTraceReplayComparisonExportArtifact artifact =
                    runtimeTraceReplayComparisonExportService.exportByTraceId(principal.userId(), traceId);
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayComparisonExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/runtime-trace/replay-comparison/export")
    public ResponseEntity<byte[]> exportReplayComparisonByMessageId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RuntimeTraceReplayComparisonExportArtifact artifact =
                    runtimeTraceReplayComparisonExportService.exportByMessageId(
                            principal.userId(), conversationId, messageId);
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayComparisonExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceReplayComparisonExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
