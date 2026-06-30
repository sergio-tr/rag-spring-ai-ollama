package com.uniovi.rag.application.service.evaluation.export;

import java.util.List;

/** Canonical Lab evaluation export contract v1 (BL-014). */
public final class EvaluationExportV1Schema {

    public static final String VERSION = "1";

    public static final String RESULTS_JSON = "results.json";
    public static final String SUMMARY_CSV = "summary.csv";
    public static final String EVALUATION_MANIFEST_JSON = "evaluation_manifest.json";
    public static final String FULL_BUNDLE_ZIP = "full-bundle.zip";

    public static final String MANIFEST_SCHEMA_VERSION = "1";

    /** Summary CSV column cap — human pivot table, not full MVP flat export. */
    public static final List<String> SUMMARY_CSV_COLUMNS =
            List.of(
                    "itemId",
                    "evaluationRunId",
                    "datasetQuestionId",
                    "question",
                    "outcome",
                    "correctness",
                    "finalScore",
                    "llmJudgeScore",
                    "normalizedExactMatch",
                    "recallAt1",
                    "latencyMs",
                    "llmModelId",
                    "presetCode",
                    "queryType",
                    "derivedErrorClass",
                    "traceId",
                    "benchmarkKind",
                    "finalScoreAvailable",
                    "answerability",
                    "sourceCount");

    private EvaluationExportV1Schema() {}
}
