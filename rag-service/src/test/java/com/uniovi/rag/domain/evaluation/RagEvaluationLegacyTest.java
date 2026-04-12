package com.uniovi.rag.domain.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEvaluationLegacyTest {

    @Test
    void exposesLegacyConstants() {
        assertEquals("LEGACY_COMBINATORIAL", RagEvaluationLegacy.LEGACY_COMBINATORIAL);
        assertEquals("legacyEvaluationMode", RagEvaluationLegacy.RESPONSE_KEY_LEGACY_MODE);
    }
}
