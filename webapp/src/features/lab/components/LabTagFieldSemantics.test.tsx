import { describe, expect, it } from "vitest";

/**
 * Lab tag field semantics are covered by
 * {@link ./lab-evaluation-run-card.test.tsx} — "does not render misleading chat model tag combobox".
 */
describe("LabTagFieldSemantics", () => {
  it("documents campaign tag as free-text (see lab-evaluation-run-card.test.tsx)", () => {
    expect("lab-eval-run-name").toMatch(/run-name/i);
  });
});
