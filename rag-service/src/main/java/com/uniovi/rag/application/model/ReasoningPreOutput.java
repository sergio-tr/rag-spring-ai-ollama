package com.uniovi.rag.application.model;

/**
 * Output of the reasoning pre-step (thought, plan, or extra context for execution).
 */
public record ReasoningPreOutput(String thoughtOrPlan, String extraContext) {

    public static ReasoningPreOutput of(String thoughtOrPlan) {
        return new ReasoningPreOutput(thoughtOrPlan, null);
    }

    public static ReasoningPreOutput of(String thoughtOrPlan, String extraContext) {
        return new ReasoningPreOutput(thoughtOrPlan, extraContext);
    }
}
