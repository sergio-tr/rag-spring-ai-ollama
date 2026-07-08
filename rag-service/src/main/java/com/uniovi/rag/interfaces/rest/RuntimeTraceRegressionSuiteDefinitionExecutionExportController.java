package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * P37: two POST routes for saved-definition execution ZIP export - delegates only to
 * {@link RuntimeTraceRegressionSuiteDefinitionExecutionExportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteDefinitionExecutionExportController {

    private final RuntimeTraceRegressionSuiteDefinitionExecutionExportService exportService;

    public RuntimeTraceRegressionSuiteDefinitionExecutionExportController(
            RuntimeTraceRegressionSuiteDefinitionExecutionExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/{definitionId}/execute/export")
    public ResponseEntity<byte[]> exportExecutionByDefinitionId(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID definitionId = definitionIdOpt.get();
        String trimmed = body == null ? "" : body.trim();
        if (!trimmed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            return toZipResponse(exportService.exportByDefinitionId(definitionId, userId));
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException ex) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException ex) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
    }

    @PostMapping("/conversations/{conversationId}/runtime-trace-regression-suite-definitions/{definitionId}/execute/export")
    public ResponseEntity<byte[]> exportExecutionByDefinitionIdAndConversation(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("conversationId") String conversationIdRaw,
            @PathVariable("definitionId") String definitionIdRaw,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        Optional<UUID> conversationIdOpt = parseUuid(conversationIdRaw);
        if (definitionIdOpt.isEmpty() || conversationIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID definitionId = definitionIdOpt.get();
        UUID conversationId = conversationIdOpt.get();
        String trimmed = body == null ? "" : body.trim();
        if (!trimmed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        try {
            return toZipResponse(
                    exportService.exportByDefinitionIdAndConversation(definitionId, conversationId, userId));
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException ex) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException ex) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
