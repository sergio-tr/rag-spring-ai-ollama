package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerabilityReviewQueueExporterTest {

    @Test
    void exportsReviewQueueCsvAndJson(@TempDir Path tempDir) throws Exception {
        RagPresetQuestion reviewQuestion =
                new RagPresetQuestion(
                        "REVIEW-1",
                        "q",
                        "No se menciona el tema; sí se decidió en el acta correspondiente.",
                        Optional.of(QueryType.BOOLEAN_QUERY),
                        Optional.empty(),
                        "",
                        List.of(),
                        List.of(),
                        "",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        "");

        AnswerabilityReviewQueueExporter.ExportResult result =
                AnswerabilityReviewQueueExporter.exportReviewQueue(
                        List.of(reviewQuestion), "abcd1234".repeat(8), tempDir);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.csvPath()).exists();
        assertThat(result.jsonPath()).exists();
        assertThat(Files.readString(result.csvPath())).contains("datasetQuestionId");
        assertThat(result.rows().get(0)).containsKeys(
                "datasetQuestionId",
                "question",
                "expectedAnswer",
                "proposedLabel",
                "answerabilitySource",
                "ruleId",
                "reason",
                "confidence",
                "queryTypeExpected",
                "notes",
                "labelledDatasetSha256",
                "answerabilityRulesVersion");
    }
}
