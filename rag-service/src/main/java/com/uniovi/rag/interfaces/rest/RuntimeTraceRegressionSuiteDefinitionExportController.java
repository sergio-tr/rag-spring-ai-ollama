package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportSizeExceededException;
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

import java.util.Optional;
import java.util.UUID;

/**
 * P38: synchronous {@code GET} definition ZIP export - delegates only to {@link RuntimeTraceRegressionSuiteDefinitionExportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteDefinitionExportController {

    private final RuntimeTraceRegressionSuiteDefinitionExportService exportService;

    public RuntimeTraceRegressionSuiteDefinitionExportController(RuntimeTraceRegressionSuiteDefinitionExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/runtime-trace-regression-suite-definitions/{definitionId}/export")
    public ResponseEntity<byte[]> exportDefinitionZip(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionIdOpt = parseUuid(definitionIdRaw);
        if (definitionIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID definitionId = definitionIdOpt.get();
        UUID userId = principal.userId();
        try {
            return toZipResponse(exportService.exportDefinitionZip(definitionId, userId));
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeTraceRegressionSuiteDefinitionExportSizeExceededException ex) {
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

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceRegressionSuiteDefinitionExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
