package com.uniovi.rag.service.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralKnowledgeQueryDetectorTest {

    @Test
    void jokeQueriesBypassRag() {
        assertTrue(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly("cuéntame un chiste"));
        assertTrue(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly("cuentame un chiste"));
        assertTrue(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly("tell me a joke"));
    }

    @Test
    void documentScopedQueriesDoNotBypass() {
        assertFalse(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly("cuéntame un chiste que salió en el acta"));
        assertFalse(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly("qué dice la reunión sobre X"));
    }

    @Test
    void nullOrBlank() {
        assertFalse(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly(null));
        assertFalse(GeneralKnowledgeQueryDetector.likelyGeneralKnowledgeOnly("   "));
    }
}
