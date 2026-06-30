package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingEvaluationGoldAlignmentTest {

    @Test
    void referenceBundle_workbookGoldIds_alignWithCorpusAndChunkRegistry() throws Exception {
        try (InputStream in = new ClassPathResource(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION).getInputStream()) {
            WorkbookParseResult parsed = new EvaluationWorkbookParser().parse(in, ExperimentalDatasetType.REFERENCE_BUNDLE);
            assertThat(parsed.validationReport().hasErrors()).isFalse();
            EvaluationWorkbook workbook = parsed.workbook();

            EvaluationGoldCorpusAlignmentVerifier.AlignmentReport report =
                    EvaluationGoldCorpusAlignmentVerifier.verifyWorkbook(workbook);
            assertThat(report.aligned())
                    .as("violations: %s", report.violations())
                    .isTrue();

            Set<String> expectedFilenames = EvaluationGoldCorpusAlignmentVerifier.expectedGoldFilenames(workbook);
            assertThat(expectedFilenames).hasSize(workbook.chunkRegistry().size());
            assertThat(expectedFilenames)
                    .allSatisfy(
                            name ->
                                    assertThat(EvaluationGoldCorpusFilenameSupport.parse(name))
                                            .isPresent());
        }
    }

    @Test
    void goldFilename_roundTripsWorkbookIds() {
        String filename = EvaluationGoldCorpusFilenameSupport.buildFilename("ACTA_1", "ACTA_1_ELEVATOR_PAINT");
        assertThat(filename).endsWith(EvaluationGoldCorpusFilenameSupport.SUFFIX);
        var parsed = EvaluationGoldCorpusFilenameSupport.parse(filename);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().evaluationDocumentId()).isEqualTo("ACTA_1");
        assertThat(parsed.get().evaluationChunkId()).isEqualTo("ACTA_1_ELEVATOR_PAINT");
    }
}
