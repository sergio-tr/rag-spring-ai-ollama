package com.uniovi.rag.testsupport.evaluation;

import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared defaults for {@link StartBenchmarkRunRequest} in unit tests. */
public final class StartBenchmarkRunRequestTestSupport {

    private StartBenchmarkRunRequestTestSupport() {}

    public static StartBenchmarkRunRequest minimalRag(List<String> experimentalPresetCodes, String embeddingModelId) {
        return new StartBenchmarkRunRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                EvaluationRunKind.PRODUCT_EXPLORATION,
                "n",
                null,
                null,
                null,
                null,
                experimentalPresetCodes,
                null,
                embeddingModelId,
                List.of(),
                List.of(),
                false,
                null,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                Map.of());
    }

    public static StartBenchmarkRunRequest minimalRagWithChatModel(
            List<String> experimentalPresetCodes, String embeddingModelId, String llmModelId) {
        return new StartBenchmarkRunRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                EvaluationRunKind.PRODUCT_EXPLORATION,
                "n",
                null,
                null,
                null,
                null,
                experimentalPresetCodes,
                llmModelId,
                embeddingModelId,
                List.of(),
                List.of(),
                false,
                null,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                Map.of());
    }

    public static StartBenchmarkRunRequest withGoldSubsetManifest(String manifestId) {
        return new StartBenchmarkRunRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                manifestId,
                null,
                Map.of());
    }
}
