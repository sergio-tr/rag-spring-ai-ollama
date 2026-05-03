package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import org.springframework.stereotype.Component;

/**
 * Groups definition-scoped run ZIP adapters injected together into {@link RuntimeTraceRegressionSuiteDefinitionController}.
 */
@Component
public final class DefinitionRunZipServiceBundle {

    private final RuntimeTraceRegressionSuiteRunExportService runExportService;
    private final RuntimeTraceRegressionSuiteRunImportService runImportService;
    private final RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

    public DefinitionRunZipServiceBundle(
            RuntimeTraceRegressionSuiteRunExportService runExportService,
            RuntimeTraceRegressionSuiteRunImportService runImportService,
            RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService) {
        this.runExportService = runExportService;
        this.runImportService = runImportService;
        this.runImportPreviewService = runImportPreviewService;
    }

    public RuntimeTraceRegressionSuiteRunExportService runExportService() {
        return runExportService;
    }

    public RuntimeTraceRegressionSuiteRunImportService runImportService() {
        return runImportService;
    }

    public RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService() {
        return runImportPreviewService;
    }
}
