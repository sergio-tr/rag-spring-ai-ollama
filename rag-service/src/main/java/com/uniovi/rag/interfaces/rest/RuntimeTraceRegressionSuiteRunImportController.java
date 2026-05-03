package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * P44: synchronous {@code POST} run ZIP import — delegates only to {@link RuntimeTraceRegressionSuiteRunImportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteRunImportController {

    private final RuntimeTraceRegressionSuiteRunImportService importService;
    private final String productBasePath;

    public RuntimeTraceRegressionSuiteRunImportController(
            RuntimeTraceRegressionSuiteRunImportService importService,
            @Value("${rag.api.product-base-path}") String productBasePath) {
        this.importService = importService;
        this.productBasePath = productBasePath;
    }

    @PostMapping("/runtime-trace-regression-suite-runs/import")
    public ResponseEntity<Void> importRunZip(@AuthenticationPrincipal RagPrincipal principal, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<byte[]> bodyOpt =
                RuntimeTraceImportRequestSupport.readZipBody(
                        request, RuntimeTraceRegressionSuiteRunImportService.MAX_IMPORT_ZIP_BYTES);
        if (bodyOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = bodyOpt.get();
        UUID userId = principal.userId();
        try {
            UUID createdId = importService.importRunZip(body, userId);
            String location = productBasePath + "/runtime-trace-regression-suite-runs/" + createdId;
            return ResponseEntity.created(URI.create(location)).build();
        } catch (RuntimeTraceRegressionSuiteRunImportRejectedException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
