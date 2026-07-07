import { describe, expect, it } from "vitest";
import {
  formatAdvancedTechnicalValidationIssue,
  formatRuntimeValidationIssueMessage,
} from "./runtime-validation-copy";
import type { RuntimeConfigValidationIssueDto } from "@/types/api";

function t(key: string): string {
  const map: Record<string, string> = {
    chatFeatureTipRequiresRetrieval: "Requires retrieval",
    chatValidation_ranker_requires_retrieval: "Rerank retrieved passages requires retrieval.",
    chatValidation_post_retrieval_requires_retrieval: "Post-retrieval processing requires retrieval.",
    chatValidation_advisor_requires_retrieval: "Retrieval advisor requires retrieval.",
    chatValidationGeneric: "Review advanced configuration for details.",
  };
  return map[key] ?? key;
}

describe("runtime-validation-copy unsupported runtime", () => {
  it.each([
    ["rankerEnabled requires useRetrieval", "Rerank retrieved passages requires retrieval."],
    ["postRetrievalEnabled requires useRetrieval", "Post-retrieval processing requires retrieval."],
    ["useAdvisor requires useRetrieval", "Retrieval advisor requires retrieval."],
  ])("maps %s to product copy", (message, expected) => {
    const issue: RuntimeConfigValidationIssueDto = {
      code: "UNSUPPORTED_RUNTIME_CONFIGURATION",
      field: null,
      message,
      severity: "ERROR",
    };
    expect(formatRuntimeValidationIssueMessage(issue, t)).toBe(expected);
    expect(formatRuntimeValidationIssueMessage(issue, t)).not.toMatch(/rankerEnabled/);
  });

  it("preserves raw diagnostic in advanced formatter", () => {
    const issue: RuntimeConfigValidationIssueDto = {
      code: "UNSUPPORTED_RUNTIME_CONFIGURATION",
      field: null,
      message: "rankerEnabled requires useRetrieval",
      severity: "ERROR",
    };
    expect(formatAdvancedTechnicalValidationIssue(issue)).toBe(
      "UNSUPPORTED_RUNTIME_CONFIGURATION: rankerEnabled requires useRetrieval",
    );
  });
});
