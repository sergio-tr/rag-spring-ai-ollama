package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewService;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * P40: synchronous {@code POST} definition ZIP import preview — delegates only to {@link RuntimeTraceRegressionSuiteDefinitionImportPreviewService}.
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
        String rawCt = request.getContentType();
        if (rawCt == null || rawCt.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(rawCt.trim());
        } catch (InvalidMediaTypeException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (!"application".equals(mediaType.getType())
                || !"zip".equals(mediaType.getSubtype())
                || !mediaType.getParameters().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (body.length == 0 || body.length > RuntimeTraceRegressionSuiteDefinitionImportPreviewService.MAX_PREVIEW_ZIP_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto previewDto =
                    previewService.previewImportZip(body);
            return ResponseEntity.ok(previewDto);
        } catch (RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
