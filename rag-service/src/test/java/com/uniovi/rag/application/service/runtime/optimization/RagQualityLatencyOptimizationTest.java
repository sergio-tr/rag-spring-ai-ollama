package com.uniovi.rag.application.service.runtime.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.FinalAnswerSynthesizer;
import com.uniovi.rag.application.service.runtime.ReasoningBlockSanitizer;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RagQualityLatencyOptimizationTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "dime qué actas tienen 20 asistentes",
                "dime los lugares donde se han realizado las actas",
                "en qué acta se hablo sobre cambios en las normas de convivencia?",
                "en qué acta se habló sobre la regulación del uso de las terrazas?",
                "en qué acta se hablo sobre las zonas comunes y qué se dice sobre ellas?",
                "dime que se ha hablado sobre mejoras en el ascensor",
                "a qué actas asiste Ana Sánchez Herrera?",
                "en qué actas se habla sobre la Reparación del sistema eléctrico del edificio"
            })
    void deterministicRewrite_preservesTopicTerms(String question) {
        Optional<StructuredRewriteResult> rewritten = DeterministicQueryRewriteShortcuts.tryRewrite(question);
        assertThat(rewritten).isPresent();
        String text = rewritten.get().rewrittenQueryText().toLowerCase();
        if (question.contains("terrazas")) {
            assertThat(text).contains("terrazas");
        }
        if (question.contains("zonas comunes")) {
            assertThat(text).contains("zonas comunes");
        }
        if (question.contains("ascensor")) {
            assertThat(text).contains("ascensor");
        }
        if (question.contains("20 asistentes")) {
            assertThat(text).contains("20");
        }
        if (question.contains("Ana Sánchez Herrera")) {
            assertThat(text.toLowerCase()).contains("ana sánchez herrera");
        }
    }

    @Test
    void condensePolicy_skipsSelfContainedActaQuestion() {
        assertThat(ConversationCondensePolicy.isSelfContainedQuestion(
                        "en qué acta se habló sobre la regulación del uso de las terrazas?"))
                .isTrue();
        assertThat(ConversationCondensePolicy.requiresMemoryReference("¿qué se decidió en esa reunión?"))
                .isTrue();
    }

    @Test
    void judgeLeakage_isStrippedFromUserAnswer() {
        String raw = "Answer: NO\nExplanation: La evidencia proporcionada está vacía.";
        assertThat(ReasoningBlockSanitizer.stripReasoningBlocks(raw)).isBlank();
        assertThat(FinalAnswerSynthesizer.sanitizeJudgeLeakage(raw)).isBlank();
    }

    @Test
    void rewriteSkipPolicy_appliesWhenDeterministicShortcutExists() {
        assertThat(DeterministicQueryRewriteShortcuts.tryRewrite("en qué acta se habló sobre terrazas"))
                .isPresent();
    }
}
