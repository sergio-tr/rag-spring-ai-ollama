package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportSizeExceededException;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * P32: two POST routes for regression-suite ZIP export. Delegates only to {@link RuntimeTraceRegressionSuiteExportService}
 * (never {@link com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService}
 * directly).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteExportController {

    private final RuntimeTraceRegressionSuiteExportService exportService;
    private final ObjectMapper strictRegressionSuiteMapper;

    public RuntimeTraceRegressionSuiteExportController(
            RuntimeTraceRegressionSuiteExportService exportService,
            @Qualifier(RegressionSuiteRestJacksonConfiguration.REGRESSION_SUITE_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictRegressionSuiteMapper) {
        this.exportService = exportService;
        this.strictRegressionSuiteMapper = strictRegressionSuiteMapper;
    }

    @PostMapping(value = "/runtime-traces/regression-suite/export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportExplicitSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        Optional<RuntimeTraceRegressionSuiteHttpAdapter.ValidatedExplicit> parsed =
                RuntimeTraceRegressionSuiteHttpAdapter.parseExplicit(body, strictRegressionSuiteMapper, userId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var v = parsed.get();
        try {
            RuntimeTraceRegressionSuiteExportArtifact artifact =
                    exportService.exportExplicit(userId, v.domainRequest(), v.acceptedBody());
            return toZipResponse(artifact);
        } catch (RuntimeTraceRegressionSuiteExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        } catch (RuntimeTraceRegressionSuiteExportNotAttemptedException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/regression-suite/export",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportConversationScopedSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("conversationId") String conversationIdRaw,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        Optional<RuntimeTraceRegressionSuiteHttpAdapter.ValidatedConversationScoped> parsed =
                RuntimeTraceRegressionSuiteHttpAdapter.parseConversationScoped(
                        conversationIdRaw, body, strictRegressionSuiteMapper, userId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var v = parsed.get();
        try {
            RuntimeTraceRegressionSuiteExportArtifact artifact =
                    exportService.exportConversationScoped(
                            userId, v.domainRequest(), v.pathConversationId(), v.acceptedBody());
            return toZipResponse(artifact);
        } catch (RuntimeTraceRegressionSuiteExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        } catch (RuntimeTraceRegressionSuiteExportNotAttemptedException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceRegressionSuiteExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
