package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewService;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * P40: synchronous {@code POST} definition ZIP import preview - delegates only to {@link RuntimeTraceRegressionSuiteDefinitionImportPreviewService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteDefinitionImportPreviewController {

    private final RuntimeTraceRegressionSuiteDefinitionImportPreviewService previewService;

    public RuntimeTraceRegressionSuiteDefinitionImportPreviewController(
            RuntimeTraceRegressionSuiteDefinitionImportPreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("/runtime-trace-regression-suite-definitions/import/preview")
    public ResponseEntity<RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto> previewImportZip(
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<byte[]> bodyOpt =
                RuntimeTraceImportRequestSupport.readZipBody(
                        request, RuntimeTraceRegressionSuiteDefinitionImportPreviewService.MAX_PREVIEW_ZIP_BYTES);
        if (bodyOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = bodyOpt.get();
        try {
            RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto previewDto =
                    previewService.previewImportZip(body);
            return ResponseEntity.ok(previewDto);
        } catch (RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
