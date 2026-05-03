package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;

import static org.mockito.Mockito.when;

/** Wires a mocked {@link DefinitionRunZipServiceBundle} to the mocked ZIP port beans in WebMvc tests. */
final class DefinitionRunZipBundleStubbing {

    private DefinitionRunZipBundleStubbing() {}

    static void linkMockBundleToZipServices(
            DefinitionRunZipServiceBundle bundle,
            RuntimeTraceRegressionSuiteRunExportService export,
            RuntimeTraceRegressionSuiteRunImportService imp,
            RuntimeTraceRegressionSuiteRunImportPreviewService preview) {
        when(bundle.runExportService()).thenReturn(export);
        when(bundle.runImportService()).thenReturn(imp);
        when(bundle.runImportPreviewService()).thenReturn(preview);
    }
}
