package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Objects;

/**
 * P45: synchronous {@code POST} run ZIP import preview — delegates only to {@link RuntimeTraceRegressionSuiteRunImportPreviewService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteRunImportPreviewController {

    private final RuntimeTraceRegressionSuiteRunImportPreviewService previewService;

    public RuntimeTraceRegressionSuiteRunImportPreviewController(
            RuntimeTraceRegressionSuiteRunImportPreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("/runtime-trace-regression-suite-runs/import/preview")
    public ResponseEntity<RuntimeTraceRegressionSuiteRunImportPreviewResponseDto> previewImportZip(
            @AuthenticationPrincipal RagPrincipal principal, HttpServletRequest request) {
        Objects.requireNonNull(principal, "principal");
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
        if (body.length == 0 || body.length > RuntimeTraceRegressionSuiteRunImportPreviewService.MAX_PREVIEW_ZIP_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RuntimeTraceRegressionSuiteRunImportPreviewResponseDto previewDto = previewService.previewImportZip(body);
            return ResponseEntity.ok(previewDto);
        } catch (RuntimeTraceRegressionSuiteRunImportPreviewRejectedException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
