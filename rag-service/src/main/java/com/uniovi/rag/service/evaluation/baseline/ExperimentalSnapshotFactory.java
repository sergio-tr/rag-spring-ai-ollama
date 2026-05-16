package com.uniovi.rag.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Builds default baseline snapshots from Spring AI properties plus optional {@link EvaluationRunEntity} overrides. */
@Component
public class ExperimentalSnapshotFactory {

    private final String defaultChatModel;
    private final String defaultEmbeddingModel;
    private final int defaultTopK;

    public ExperimentalSnapshotFactory(
            @Value("${spring.ai.ollama.chat.model:gemma3:4b}") String defaultChatModel,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large}") String defaultEmbeddingModel,
            @Value("${spring.ai.ollama.top-k:10}") int defaultTopK) {
        this.defaultChatModel = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
        this.defaultTopK = Math.max(1, defaultTopK);
    }

    public LlmExperimentalSnapshot buildLlmSnapshot(EvaluationRunEntity run) {
        List<String> unsupported = new ArrayList<>();
        unsupported.add("minP"); // Not exposed on Spring AI OllamaOptions builder (1.0.0-M6).
        String model =
                run != null && run.getLlmModelId() != null && !run.getLlmModelId().isBlank()
                        ? run.getLlmModelId().trim()
                        : defaultChatModel;
        return new LlmExperimentalSnapshot(
                model,
                0.2,
                0.9,
                defaultTopK,
                null,
                1.05,
                8192,
                512,
                null,
                42,
                List.of(),
                null,
                Boolean.FALSE,
                unsupported);
    }

    public EmbeddingExperimentalSnapshot buildEmbeddingSnapshot(EvaluationRunEntity run) {
        List<String> unsupported = new ArrayList<>();
        unsupported.add("normalize");
        unsupported.add("queryPrefix");
        unsupported.add("passagePrefix");
        unsupported.add("batchSize");
        String model =
                run != null && run.getEmbeddingModelId() != null && !run.getEmbeddingModelId().isBlank()
                        ? run.getEmbeddingModelId().trim()
                        : defaultEmbeddingModel;
        Integer dim =
                run != null && run.getEmbeddingDimensions() != null ? run.getEmbeddingDimensions() : null;
        return new EmbeddingExperimentalSnapshot(model, dim, null, null, null, null, "MODEL_DEFAULT", unsupported);
    }
}
