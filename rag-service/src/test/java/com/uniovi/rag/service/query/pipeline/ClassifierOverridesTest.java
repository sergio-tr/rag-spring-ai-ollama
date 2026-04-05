package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassifierOverridesTest {

    @Test
    void presencePhrasesMapToBooleanQuery() {
        assertEquals(QueryType.BOOLEAN_QUERY, ClassifierOverrides.apply("confirma si aparece X", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void noOverrideReturnsOriginal() {
        assertEquals(QueryType.COUNT_DOCUMENTS, ClassifierOverrides.apply("cuántos documentos hay", QueryType.COUNT_DOCUMENTS));
    }
}
