package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrefixOnlyAnswerGuardTest {

    @Test
    void detectsLiteralBased() {
        assertThat(PrefixOnlyAnswerGuard.isPrefixOnlyFragment("Based")).isTrue();
        assertThat(PrefixOnlyAnswerGuard.isPrefixOnlyFragment("Based\n\nFuentes consultadas: ACTA 1.pdf."))
                .isTrue();
    }

    @Test
    void detectsGroundingPrefixWithoutSubstance() {
        assertThat(PrefixOnlyAnswerGuard.isPrefixOnlyFragment("Based on")).isTrue();
        assertThat(PrefixOnlyAnswerGuard.isPrefixOnlyFragment("Según las fuentes")).isTrue();
    }

    @Test
    void acceptsSubstantiveGroundedAnswer() {
        assertThat(
                        PrefixOnlyAnswerGuard.isPrefixOnlyFragment(
                                "Based on ACTA 1.pdf, las cámaras de seguridad se discutieron en febrero."))
                .isFalse();
    }

    @Test
    void resolvesToAbstentionWhenNoSources() {
        String out =
                PrefixOnlyAnswerGuard.resolve(
                        "Based",
                        "en qué actas se habla sobre cámaras",
                        List.of());
        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void resolvesToGroundedFallbackWhenSourcesExist() {
        List<Map<String, Object>> sources =
                List.of(Map.of("filename", "ACTA 1.pdf"), Map.of("filename", "ACTA 6.pdf"));
        String out =
                PrefixOnlyAnswerGuard.resolve(
                        "Based\n\nFuentes consultadas: ACTA 1.pdf.",
                        "en qué actas se habla sobre cámaras",
                        sources);
        assertThat(out).contains("ACTA 1.pdf", "ACTA 6.pdf");
        assertThat(out).doesNotContain("Based");
    }

    @Test
    void qx_unsupportedTopic_abstainsWithoutSources() {
        String out =
                PrefixOnlyAnswerGuard.resolve(
                        "Based on",
                        "qué acta habla sobre la instalación de una piscina climatizada en la azotea?",
                        List.of());
        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void resolveDraftUsesAbstentionWhenContextMissing() {
        String out =
                PrefixOnlyAnswerGuard.resolveDraft(
                        "Based", "cuenta las actas en las que", "");
        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }
}
