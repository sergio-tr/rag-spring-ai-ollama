package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportArtifact;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportSizeExceededException;
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
 * P23: owner-scoped GET download of a single standalone replay ZIP. Delegates only to
 * {@link RuntimeTraceReplayExportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayExportController {

    private final RuntimeTraceReplayExportService runtimeTraceReplayExportService;

    public RuntimeTraceReplayExportController(RuntimeTraceReplayExportService runtimeTraceReplayExportService) {
        this.runtimeTraceReplayExportService = runtimeTraceReplayExportService;
    }

    @GetMapping("/runtime-traces/{traceId}/replay/export")
    public ResponseEntity<byte[]> exportReplayByTraceId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID traceId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RuntimeTraceReplayExportArtifact artifact =
                    runtimeTraceReplayExportService.exportByTraceId(principal.userId(), traceId);
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/runtime-trace/replay/export")
    public ResponseEntity<byte[]> exportReplayByMessageId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RuntimeTraceReplayExportArtifact artifact =
                    runtimeTraceReplayExportService.exportByMessageId(
                            principal.userId(), conversationId, messageId);
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceReplayExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
