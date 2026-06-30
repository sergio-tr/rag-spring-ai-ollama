import { describe, expect, it } from "vitest";
import { formatRuntimeValidationIssueMessage } from "./runtime-validation-copy";
import type { RuntimeConfigValidationIssueDto } from "@/types/api";

function t(key: string): string {
  const map: Record<string, string> = {
    chatValidation_TOOLS_FUNCTION_CALLING_PRECEDENCE:
      "Function calling is used when both tools and function calling are enabled.",
    chatValidationGeneric: "Review advanced configuration for details.",
  };
  return map[key] ?? key;
}

describe("runtime-validation-copy", () => {
  it("maps TOOLS_FUNCTION_CALLING_PRECEDENCE without leaking internal routing copy", () => {
    const issue: RuntimeConfigValidationIssueDto = {
      code: "TOOLS_FUNCTION_CALLING_PRECEDENCE",
      field: null,
      message:
        "Tools and function calling are both enabled. Function calling takes precedence over deterministic tools.",
      severity: "WARNING",
    };
    const out = formatRuntimeValidationIssueMessage(issue, t);
    expect(out).toBe("Function calling is used when both tools and function calling are enabled.");
    expect(out).not.toMatch(/deterministic tool/i);
    expect(out).not.toMatch(/takes precedence/i);
  });

  it("sanitizes unknown issues with forbidden phrases", () => {
    const issue: RuntimeConfigValidationIssueDto = {
      code: "UNKNOWN",
      field: null,
      message: "resolved config missing profile hash",
      severity: "WARNING",
    };
    expect(formatRuntimeValidationIssueMessage(issue, t)).toBe("Review advanced configuration for details.");
  });
});
