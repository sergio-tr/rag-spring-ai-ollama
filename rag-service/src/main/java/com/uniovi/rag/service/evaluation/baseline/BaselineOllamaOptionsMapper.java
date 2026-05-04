package com.uniovi.rag.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.ArrayList;
import java.util.List;

/** Maps {@link LlmExperimentalSnapshot} into Spring AI {@link OllamaOptions} for reproducible non-streaming calls. */
public final class BaselineOllamaOptionsMapper {

    private BaselineOllamaOptionsMapper() {}

    public static OllamaOptions toOllamaOptions(LlmExperimentalSnapshot snapshot) {
        List<String> sink = new ArrayList<>();
        return toOllamaOptions(snapshot, sink);
    }

    public static OllamaOptions toOllamaOptions(LlmExperimentalSnapshot snapshot, List<String> unsupportedSink) {
        var b = OllamaOptions.builder().model(snapshot.model());
        if (snapshot.temperature() != null) {
            b.temperature(snapshot.temperature());
        }
        if (snapshot.topP() != null) {
            b.topP(snapshot.topP());
        }
        if (snapshot.topK() != null) {
            b.topK(snapshot.topK());
        }
        if (snapshot.repeatPenalty() != null) {
            b.repeatPenalty(snapshot.repeatPenalty());
        }
        if (snapshot.numCtx() != null) {
            b.numCtx(snapshot.numCtx());
        }
        if (snapshot.seed() != null) {
            b.seed(snapshot.seed());
        }
        Integer predict = snapshot.numPredict() != null ? snapshot.numPredict() : snapshot.maxTokens();
        if (predict != null) {
            b.numPredict(predict);
        }
        if (snapshot.stopSequences() != null && !snapshot.stopSequences().isEmpty()) {
            b.stop(snapshot.stopSequences());
        }
        if (snapshot.outputFormat() != null) {
            b.format(snapshot.outputFormat());
        }
        if (snapshot.minP() != null && unsupportedSink != null) {
            unsupportedSink.add("minP_runtime_on_record_only");
        }
        if (Boolean.TRUE.equals(snapshot.streaming()) && unsupportedSink != null) {
            unsupportedSink.add("streaming_eval_uses_sync_call");
        }
        return b.build();
    }
}
