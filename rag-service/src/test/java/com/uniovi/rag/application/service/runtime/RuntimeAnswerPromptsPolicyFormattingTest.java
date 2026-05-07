package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeAnswerPromptsPolicyFormattingTest {

    @Test
    void ragUserTurn_documentScoped_formatsWithoutIllegalPercent() {
        assertThatCode(
                        () ->
                                RuntimeAnswerPrompts.ragUserTurn(
                                        "¿Acta del 25 de febrero de 2025?",
                                        "CTX",
                                        AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT,
                                        true,
                                        Optional.of("pre"),
                                        null))
                .doesNotThrowAnyException();
    }
}
