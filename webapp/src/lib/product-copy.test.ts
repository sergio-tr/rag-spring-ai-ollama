import { describe, expect, it } from "vitest";
import {
  FORBIDDEN_PRIMARY_UI_PATTERNS,
  formatBenchmarkKindLabel,
  formatChatExperimentalPresetOptionLabel,
  formatClassifierFallbackNote,
  formatPresetSupportMessage,
  sanitizeLabPrimarySurfaceCopy,
} from "./product-copy";

const labT = (key: string) => {
  const map: Record<string, string> = {
    "benchmarkKindLabel.llm": "LLM evaluation",
    "benchmarkKindLabel.ragPreset": "RAG preset evaluation",
    "benchmarkKindLabel.unknown": "Evaluation",
    "labConfigNotSingleTurn": "This preset is not available for this evaluation type.",
    "labConfigP13": "This preset is not available for this evaluation type. Use Chat for multi-turn flows.",
    "labConfigRequiresIndex": "A compatible index is required before running this evaluation.",
    "labConfigUnsupportedPreset": "This experimental preset is not supported.",
    "presetSupportStatus.requiresMultiTurn":
      "This preset is not available for this evaluation type. Use Chat for multi-turn flows.",
    "chatPresetNotSelectable": "Not selectable in Chat for this configuration.",
    "chatClassifierFallbackUnavailable": "Classifier was unavailable; a fallback path was used.",
    "chatClassifierFallbackInvalidOutput": "Classifier returned invalid output; a fallback path was used.",
  };
  return map[key] ?? key;
};

describe("product-copy humanizers", () => {
  it("formatBenchmarkKindLabel maps raw benchmark enums to readable labels", () => {
    expect(formatBenchmarkKindLabel("RAG_PRESET_END_TO_END", labT)).toBe("RAG preset evaluation");
    expect(formatBenchmarkKindLabel("LLM_JUDGE_QA", labT)).toBe("LLM evaluation");
    expect(formatBenchmarkKindLabel(null, labT)).toBe("Evaluation");
  });

  it("formatPresetSupportMessage humanizes reason and status codes", () => {
    expect(formatPresetSupportMessage(null, "FUTURE_MULTI_TURN_NOT_SELECTABLE", labT)).toBe(
      "This preset is not available for this evaluation type.",
    );
    expect(formatPresetSupportMessage("REQUIRES_MULTI_TURN", null, labT)).toBe(
      "This preset is not available for this evaluation type. Use Chat for multi-turn flows.",
    );
    expect(formatPresetSupportMessage(null, "FEATURE_REQUIRES_INDEX", labT)).toBe(
      "A compatible index is required before running this evaluation.",
    );
  });

  it("formatChatExperimentalPresetOptionLabel avoids raw status enums in labels", () => {
    const label = formatChatExperimentalPresetOptionLabel(
      {
        code: "P13",
        label: "Clarification loop",
        supported: false,
        supportStatus: "REQUIRES_MULTI_TURN",
        reasonIfUnsupported: "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED",
        requiresMultiTurn: true,
        chatSelectable: false,
      },
      labT,
    );
    expect(label).toContain("P13 — Clarification loop");
    expect(label).not.toMatch(/REQUIRES_MULTI_TURN/);
    expect(label).not.toMatch(/PRESET_CLARIFICATION/);
    expect(label).toMatch(/not available for this evaluation type/i);
  });

  it("formatClassifierFallbackNote humanizes classifier fallback values", () => {
    expect(formatClassifierFallbackNote("UNAVAILABLE: timeout", labT)).toMatch(/unavailable/i);
    expect(formatClassifierFallbackNote("INVALID_OUTPUT", labT)).toMatch(/invalid output/i);
  });

  it("FORBIDDEN_PRIMARY_UI_PATTERNS cover raw enum and evidence strings", () => {
    const samples = [
      "RAG_PRESET_END_TO_END",
      "FUTURE_MULTI_TURN_NOT_SELECTABLE",
      "M9 experimental evidence",
      "Do not claim",
      "Lab evaluation corpus",
      "Missing preferred tags: llama3.1:8b",
    ];
    for (const sample of samples) {
      expect(FORBIDDEN_PRIMARY_UI_PATTERNS.some((re) => re.test(sample))).toBe(true);
    }
  });

  it("sanitizeLabPrimarySurfaceCopy replaces forbidden API-derived labels", () => {
    expect(sanitizeLabPrimarySurfaceCopy("Lab evaluation corpus", "Knowledge base")).toBe("Knowledge base");
    expect(sanitizeLabPrimarySurfaceCopy("My KB", "Knowledge base")).toBe("My KB");
  });
});
