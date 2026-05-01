package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportArtifact;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportSizeLimitExceededException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceExportController {

    private static final Set<String> BUNDLE_ALLOWED_QUERY_PARAMS = Set.of("createdAtFrom", "createdAtTo", "workflowName");

    private final RuntimeTraceExportService runtimeTraceExportService;

    public RuntimeTraceExportController(RuntimeTraceExportService runtimeTraceExportService) {
        this.runtimeTraceExportService = runtimeTraceExportService;
    }

    @GetMapping("/runtime-traces/{traceId}/export")
    public ResponseEntity<byte[]> exportSingleByTraceId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID traceId,
            HttpServletRequest request
    ) {
        rejectUnsupportedQueryParams(request, Set.of());
        try {
            return toZipResponse(runtimeTraceExportService.exportSingleTraceById(principal.userId(), traceId));
        } catch (RuntimeTraceExportSizeLimitExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/runtime-trace/export")
    public ResponseEntity<byte[]> exportSingleByMessageId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            HttpServletRequest request
    ) {
        rejectUnsupportedQueryParams(request, Set.of());
        try {
            return toZipResponse(
                    runtimeTraceExportService.exportSingleTraceByMessageId(principal.userId(), conversationId, messageId));
        } catch (RuntimeTraceExportSizeLimitExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    @GetMapping("/conversations/{conversationId}/runtime-traces/export")
    public ResponseEntity<byte[]> exportConversationBundle(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam(name = "createdAtFrom", required = false) Instant createdAtFrom,
            @RequestParam(name = "createdAtTo", required = false) Instant createdAtTo,
            @RequestParam(name = "workflowName", required = false) String workflowName,
            HttpServletRequest request
    ) {
        rejectUnsupportedQueryParams(request, BUNDLE_ALLOWED_QUERY_PARAMS);
        try {
            return toZipResponse(
                    runtimeTraceExportService.exportConversationBundle(
                            principal.userId(),
                            conversationId,
                            Optional.ofNullable(createdAtFrom),
                            Optional.ofNullable(createdAtTo),
                            Optional.ofNullable(workflowName)));
        } catch (RuntimeTraceExportSizeLimitExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        }
    }

    private ResponseEntity<byte[]> toZipResponse(RuntimeTraceExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }

    private static void rejectUnsupportedQueryParams(HttpServletRequest request, Set<String> allowed) {
        for (String key : request.getParameterMap().keySet()) {
            if (!allowed.contains(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported query parameter: " + key);
            }
        }
    }
}

