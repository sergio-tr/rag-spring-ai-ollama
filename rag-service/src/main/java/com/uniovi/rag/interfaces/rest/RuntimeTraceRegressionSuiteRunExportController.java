package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportSizeExceededException;
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
 * P43: synchronous {@code GET} run ZIP export — delegates only to {@link RuntimeTraceRegressionSuiteRunExportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteRunExportController {

    private final RuntimeTraceRegressionSuiteRunExportService exportService;

    public RuntimeTraceRegressionSuiteRunExportController(RuntimeTraceRegressionSuiteRunExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/runtime-trace-regression-suite-runs/{runId}/export")
    public ResponseEntity<byte[]> exportRunZip(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("runId") String runIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> runIdOpt = parseUuid(runIdRaw);
        if (runIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID runId = runIdOpt.get();
        UUID userId = principal.userId();
        try {
            return toZipResponse(exportService.exportRunZip(runId, userId));
        } catch (NotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeTraceRegressionSuiteRunExportSizeExceededException ex) {
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

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceRegressionSuiteRunExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
