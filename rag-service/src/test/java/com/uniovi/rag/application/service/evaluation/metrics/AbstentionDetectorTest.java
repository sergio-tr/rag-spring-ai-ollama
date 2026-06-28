package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstentionDetectorTest {

    @Test
    void runtimeMetadata_takesPrecedence() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("abstentionTriggered", true);
        mp.put("abstentionReason", "no_document_evidence");

        AbstentionDetector.Result result = AbstentionDetector.detect(mp, "Paris");

        assertThat(result.abstained()).isTrue();
        assertThat(result.source()).isEqualTo("RUNTIME_METADATA");
        assertThat(result.reason()).isEqualTo("no_document_evidence");
    }

    @Test
    void phraseMatch_detectsInsufficientEvidence() {
        AbstentionDetector.Result result =
                AbstentionDetector.detect(Map.of(), "Insufficient evidence to answer.");

        assertThat(result.abstained()).isTrue();
        assertThat(result.source()).isEqualTo("ANSWER_TEXT");
    }

    @Test
    void canonicalInsufficientMessage_detected() {
        AbstentionDetector.Result result =
                AbstentionDetector.detect(Map.of(), RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN);

        assertThat(result.abstained()).isTrue();
    }

    @Test
    void shortAnswer_notClassifiedAsAbstention() {
        AbstentionDetector.Result result = AbstentionDetector.detect(Map.of(), "Paris");

        assertThat(result.abstained()).isFalse();
        assertThat(result.source()).isEqualTo("NONE");
    }

    @Test
    void emptyAnswer_notAbstention() {
        AbstentionDetector.Result result = AbstentionDetector.detect(Map.of(), "");

        assertThat(result.abstained()).isFalse();
        assertThat(result.source()).isEqualTo("EMPTY_ANSWER");
    }
}
