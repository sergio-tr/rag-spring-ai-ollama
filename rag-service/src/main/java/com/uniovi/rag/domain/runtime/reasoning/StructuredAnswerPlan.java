package com.uniovi.rag.domain.runtime.reasoning;

import java.util.List;
import java.util.Objects;

/**
 * Safe, non-CoT answer plan meant to guide controlled generation.
 *
 * <p>This object must not contain free-form chain-of-thought. It should only contain
 * structured, user-safe planning hints (what to verify, what evidence to use, format constraints).</p>
 */
public record StructuredAnswerPlan(
        String strategy,
        String objective,
        List<String> expectedEvidence,
        List<String> answerConstraints,
        List<String> verificationChecklist,
        String safeSummary
) {
    public StructuredAnswerPlan {
        strategy = strategy == null ? "" : strategy.trim();
        objective = objective == null ? "" : objective.trim();
        expectedEvidence = List.copyOf(Objects.requireNonNullElse(expectedEvidence, List.of()));
        answerConstraints = List.copyOf(Objects.requireNonNullElse(answerConstraints, List.of()));
        verificationChecklist = List.copyOf(Objects.requireNonNullElse(verificationChecklist, List.of()));
        safeSummary = safeSummary == null ? "" : safeSummary.trim();
    }

    /**
     * Compact prompt block for injection into user-turn prompts.
     * Must remain short and not include hidden reasoning.
     */
    public String toPromptBlock(int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("<AnswerPlan>\n");
        if (!objective.isBlank()) {
            sb.append("- Objective: ").append(objective).append("\n");
        }
        for (String c : answerConstraints) {
            if (c != null && !c.isBlank()) {
                sb.append("- Constraint: ").append(c.trim()).append("\n");
            }
        }
        for (String v : verificationChecklist) {
            if (v != null && !v.isBlank()) {
                sb.append("- Verify: ").append(v.trim()).append("\n");
            }
        }
        sb.append("</AnswerPlan>");
        String out = sb.toString();
        if (maxChars <= 0 || out.length() <= maxChars) {
            return out;
        }
        return out.substring(0, maxChars);
    }
}

