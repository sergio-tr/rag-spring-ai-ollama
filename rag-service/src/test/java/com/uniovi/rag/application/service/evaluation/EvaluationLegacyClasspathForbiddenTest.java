package com.uniovi.rag.application.service.evaluation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression: legacy single-sheet workbook must not ship under {@code src/main/resources/evaluation/}.
 *
 * <p>(Stale copies could linger under {@code target/classes}; CI runs {@code clean} first.)
 */
class EvaluationLegacyClasspathForbiddenTest {

    @Test
    void evaluation_dataset_xlsx_must_not_exist_under_main_evaluation_resources() {
        Path mainEvalLegacy =
                Path.of("src/main/resources/evaluation/evaluation_dataset.xlsx").toAbsolutePath().normalize();
        assertFalse(
                Files.exists(mainEvalLegacy),
                "evaluation/evaluation_dataset.xlsx must not exist under src/main/resources/evaluation — use typed templates + reference bundle only");
    }
}
