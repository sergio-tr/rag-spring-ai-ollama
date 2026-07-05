import { describe, expect, it } from "vitest";
import {
  createNumericDraftFromValue,
  parseSimilarityDraft,
  parseTopKDraft,
  shouldShowDraftError,
} from "./retrieval-numeric-draft";

const topKConstraints = { min: 1, max: 100 };
const similarityConstraints = { min: 0, max: 1 };

describe("parseTopKDraft", () => {
  it("accepts valid integers", () => {
    expect(parseTopKDraft("1", topKConstraints)).toMatchObject({ isValid: true, parsedValue: 1 });
    expect(parseTopKDraft("12", topKConstraints)).toMatchObject({ isValid: true, parsedValue: 12 });
  });

  it("rejects zero, non-numeric, and out-of-range values", () => {
    expect(parseTopKDraft("0", topKConstraints).isValid).toBe(false);
    expect(parseTopKDraft("abc", topKConstraints).error).toBe("invalid");
    expect(parseTopKDraft("101", topKConstraints).error).toBe("range");
  });

  it("allows empty draft while typing", () => {
    expect(parseTopKDraft("", topKConstraints)).toMatchObject({
      isValid: false,
      parsedValue: null,
      error: "required",
    });
  });
});

describe("parseSimilarityDraft", () => {
  it("accepts valid thresholds", () => {
    expect(parseSimilarityDraft("0", similarityConstraints)).toMatchObject({ isValid: true, parsedValue: 0 });
    expect(parseSimilarityDraft("0.5", similarityConstraints)).toMatchObject({ isValid: true, parsedValue: 0.5 });
    expect(parseSimilarityDraft("1", similarityConstraints)).toMatchObject({ isValid: true, parsedValue: 1 });
  });

  it("allows partial decimal drafts", () => {
    const draft = parseSimilarityDraft("0.", similarityConstraints);
    expect(draft.isValid).toBe(false);
    expect(draft.parsedValue).toBeNull();
    expect(draft.error).toBeUndefined();
  });

  it("rejects invalid thresholds", () => {
    expect(parseSimilarityDraft("1.5", similarityConstraints).error).toBe("range");
    expect(parseSimilarityDraft("-0.1", similarityConstraints).error).toBe("range");
    expect(parseSimilarityDraft("abc", similarityConstraints).error).toBe("invalid");
  });
});

describe("shouldShowDraftError", () => {
  it("hides required errors while focused", () => {
    const draft = parseTopKDraft("", topKConstraints);
    expect(shouldShowDraftError(draft, { focused: true, touched: true })).toBe(false);
    expect(shouldShowDraftError(draft, { focused: false, touched: true })).toBe(true);
  });

  it("shows invalid errors immediately", () => {
    const draft = parseTopKDraft("abc", topKConstraints);
    expect(shouldShowDraftError(draft, { focused: true, touched: true })).toBe(true);
  });
});

describe("createNumericDraftFromValue", () => {
  it("formats committed values", () => {
    expect(createNumericDraftFromValue(0.55)).toEqual({
      text: "0.55",
      parsedValue: 0.55,
      isValid: true,
    });
  });
});
