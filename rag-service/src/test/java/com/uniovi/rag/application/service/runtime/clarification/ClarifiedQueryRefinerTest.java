package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClarifiedQueryRefinerTest {

    private final ClarifiedQueryRefiner refiner = new ClarifiedQueryRefiner();

    @Test
    void refine_usesFrozenMergeContract_noTrimming() {
        PendingClarificationState pending =
                new PendingClarificationState(
                        PendingClarificationState.SCHEMA_VERSION,
                        UUID.randomUUID(),
                        " base ",
                        ClarificationQuestionKind.MISSING_DATE.templateText(),
                        ClarificationQuestionKind.MISSING_DATE,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "corr");
        String out = refiner.refine(pending, " next Tuesday ");
        assertThat(out)
                .isEqualTo(
                        "BASE: base \nQUESTION:Which date or meeting are you referring to?\nANSWER: next Tuesday ");
    }

    @Test
    void refine_rejectsNullPending() {
        assertThrows(IllegalArgumentException.class, () -> refiner.refine(null, "x"));
    }
}
