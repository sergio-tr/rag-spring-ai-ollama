package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.ReasoningPreOutput;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleReasoningStrategyTest {

    private final SimpleReasoningStrategy strategy = new SimpleReasoningStrategy();

    @Test
    void runPreStep_returnsEmptyThought() {
        ReasoningPreOutput out = strategy.runPreStep(
                "query",
                QueryType.COUNT_DOCUMENTS,
                new JSONObject(),
                "expanded"
        );
        assertNotNull(out);
        assertEquals("", out.thoughtOrPlan());
        assertNull(out.extraContext());
    }

    @Test
    void runPreStep_withNullClassification() {
        ReasoningPreOutput out = strategy.runPreStep("q", null, null, null);
        assertNotNull(out);
        assertEquals("", out.thoughtOrPlan());
    }
}
