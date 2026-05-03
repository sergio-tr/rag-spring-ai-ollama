package com.uniovi.rag.application.service.runtime.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportArtifact;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportArtifact;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportArtifact;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportArtifact;
import org.junit.jupiter.api.Test;

/**
 * Exercise Zip-backed export artifact records' equals/hashCode/toString for Sonar/JaCoCo on new code.
 */
class ZipBackedRuntimeExportArtifactsTest {

    @Test
    void regression_suite_definition_execution_export_symmetric() {
        byte[] content = {1, 2};
        RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact a =
                new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact("f.zip", "application/zip", content, 2);
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo("x");
        assertThat(a).isEqualTo(
                new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
                        "f.zip", "application/zip", new byte[] {1, 2}, 2));
        assertThat(a).isNotEqualTo(
                new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
                        "f.zip", "application/zip", new byte[] {9}, 1));
        assertThat(a.hashCode())
                .isEqualTo(new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
                                "f.zip", "application/zip", new byte[] {1, 2}, 2)
                        .hashCode());
        assertThat(a.toString()).contains("RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact");
    }

    @Test
    void regression_suite_definition_export_symmetric() {
        byte[] content = {3};
        RuntimeTraceRegressionSuiteDefinitionExportArtifact a =
                new RuntimeTraceRegressionSuiteDefinitionExportArtifact("a.zip", "application/zip", content, 1);
        assertThat(a).isEqualTo(new RuntimeTraceRegressionSuiteDefinitionExportArtifact(
                "a.zip", "application/zip", new byte[] {3}, 1));
        assertThat(a).isNotEqualTo(new RuntimeTraceRegressionSuiteDefinitionExportArtifact(
                "b.zip", "application/zip", content, 1));
        assertThat(a.toString()).contains("RuntimeTraceRegressionSuiteDefinitionExportArtifact");
    }

    @Test
    void regression_suite_export_symmetric() {
        RuntimeTraceRegressionSuiteExportArtifact a =
                new RuntimeTraceRegressionSuiteExportArtifact("x.zip", RuntimeTraceRegressionSuiteExportArtifact.MEDIA_TYPE_ZIP, new byte[0], 0);
        assertThat(a).isEqualTo(new RuntimeTraceRegressionSuiteExportArtifact(
                "x.zip", RuntimeTraceRegressionSuiteExportArtifact.MEDIA_TYPE_ZIP, new byte[0], 0));
        assertThat(a.toString()).contains("RuntimeTraceRegressionSuiteExportArtifact");
    }

    @Test
    void regression_suite_run_export_symmetric() {
        RuntimeTraceRegressionSuiteRunExportArtifact a =
                new RuntimeTraceRegressionSuiteRunExportArtifact("r.zip", "application/zip", new byte[] {5}, 1);
        assertThat(a).isEqualTo(new RuntimeTraceRegressionSuiteRunExportArtifact(
                "r.zip", "application/zip", new byte[] {5}, 1));
        assertThat(a.toString()).contains("RuntimeTraceRegressionSuiteRunExportArtifact");
    }

    @Test
    void replay_batch_export_symmetric() {
        RuntimeTraceReplayBatchExportArtifact a =
                new RuntimeTraceReplayBatchExportArtifact("b.zip", "application/zip", new byte[] {8}, 1);
        assertThat(a).isEqualTo(new RuntimeTraceReplayBatchExportArtifact(
                "b.zip", "application/zip", new byte[] {8}, 1));
        assertThat(a.toString()).contains("RuntimeTraceReplayBatchExportArtifact");
    }

    @Test
    void comparison_batch_export_symmetric() {
        RuntimeTraceReplayComparisonBatchExportArtifact a =
                new RuntimeTraceReplayComparisonBatchExportArtifact("c.zip", "application/zip", new byte[] {9}, 1);
        assertThat(a).isEqualTo(new RuntimeTraceReplayComparisonBatchExportArtifact(
                "c.zip", "application/zip", new byte[] {9}, 1));
        assertThat(a.toString()).contains("RuntimeTraceReplayComparisonBatchExportArtifact");
    }

    @Test
    void comparison_export_symmetric() {
        RuntimeTraceReplayComparisonExportArtifact a =
                new RuntimeTraceReplayComparisonExportArtifact("d.zip", "application/zip", new byte[] {7}, 1);
        assertThat(a).isEqualTo(new RuntimeTraceReplayComparisonExportArtifact(
                "d.zip", "application/zip", new byte[] {7}, 1));
        assertThat(a.toString()).contains("RuntimeTraceReplayComparisonExportArtifact");
    }

    @Test
    void replay_export_symmetric() {
        RuntimeTraceReplayExportArtifact a =
                new RuntimeTraceReplayExportArtifact("e.zip", RuntimeTraceReplayExportArtifact.MEDIA_TYPE_ZIP, new byte[] {2}, 1);
        assertThat(a).isEqualTo(new RuntimeTraceReplayExportArtifact(
                "e.zip", RuntimeTraceReplayExportArtifact.MEDIA_TYPE_ZIP, new byte[] {2}, 1));
        assertThat(a.toString()).contains("RuntimeTraceReplayExportArtifact");
    }
}
