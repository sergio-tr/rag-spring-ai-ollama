package com.uniovi.rag.domain.evaluation.workbook;

/** Row from {@code llm_role_eval_cases} sheet for role-profile-scoped LLM evaluation. */
public record LlmRoleEvalCase(
        String caseId,
        String subset,
        String roleFamily,
        String roleProfile,
        String input,
        String context,
        String expectedOutput,
        String expectedKeywords,
        String forbiddenTerms,
        String scoringType,
        String requiredJsonKeys,
        String notes) {

    public LlmRoleEvalCase {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId required");
        }
        input = input != null ? input : "";
        context = context != null ? context : "";
        expectedOutput = expectedOutput != null ? expectedOutput : "";
    }
}
