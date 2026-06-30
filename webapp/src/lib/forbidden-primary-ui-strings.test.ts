import { describe, expect, it } from "vitest";
import {
  containsForbiddenPrimaryUiString,
  FORBIDDEN_NORMAL_UI_STRING_PATTERNS,
  sanitizePrimaryUiCopy,
} from "./forbidden-primary-ui-strings";

describe("forbidden-primary-ui-strings", () => {
  it("detects area-13 forbidden technical phrases", () => {
    const samples = [
      "profile hash abc",
      "prompt bundle hash xyz",
      "runtime override keys",
      "resolved config snapshot",
      "deterministic tool routing",
      "Function calling takes precedence over deterministic tools.",
      "Active snapshot id",
    ];
    for (const sample of samples) {
      expect(containsForbiddenPrimaryUiString(sample), sample).toBe(true);
    }
  });

  it("allows product-facing copy", () => {
    expect(containsForbiddenPrimaryUiString("Saved configuration state")).toBe(false);
    expect(containsForbiddenPrimaryUiString("Configuration identifier")).toBe(false);
    expect(containsForbiddenPrimaryUiString("Advanced technical details")).toBe(false);
  });

  it("sanitizes forbidden API copy to fallback", () => {
    const fallback = "Review advanced configuration for details.";
    expect(
      sanitizePrimaryUiCopy(
        "Tools and function calling are both enabled. Function calling takes precedence over deterministic tools.",
        fallback,
      ),
    ).toBe(fallback);
  });

  it("exports patterns for visibility tests", () => {
    expect(FORBIDDEN_NORMAL_UI_STRING_PATTERNS.length).toBeGreaterThan(0);
  });
});
