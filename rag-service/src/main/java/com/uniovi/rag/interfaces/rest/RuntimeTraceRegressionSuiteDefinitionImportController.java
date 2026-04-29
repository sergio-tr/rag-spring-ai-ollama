package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * P39: synchronous {@code POST} definition ZIP import — delegates only to {@link RuntimeTraceRegressionSuiteDefinitionImportService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteDefinitionImportController {

    private final RuntimeTraceRegressionSuiteDefinitionImportService importService;
    private final String productBasePath;

    public RuntimeTraceRegressionSuiteDefinitionImportController(
            RuntimeTraceRegressionSuiteDefinitionImportService importService,
            @Value("${rag.api.product-base-path}") String productBasePath) {
        this.importService = importService;
        this.productBasePath = productBasePath;
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/import")
    public ResponseEntity<Void> importDefinitionZip(
            @AuthenticationPrincipal RagPrincipal principal, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<byte[]> bodyOpt =
                RuntimeTraceImportRequestSupport.readZipBody(
                        request, RuntimeTraceRegressionSuiteDefinitionImportService.MAX_IMPORT_ZIP_BYTES);
        if (bodyOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = bodyOpt.get();
        UUID userId = principal.userId();
        try {
            UUID createdId = importService.importDefinitionZip(body, userId);
            String location = productBasePath + "/runtime-trace-regression-suite-definitions/" + createdId;
            return ResponseEntity.created(URI.create(location)).build();
        } catch (RuntimeTraceRegressionSuiteDefinitionImportRejectedException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
