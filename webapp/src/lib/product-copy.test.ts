import { describe, expect, it } from "vitest";
import {
  FORBIDDEN_PRIMARY_UI_PATTERNS,
  benchmarkKindI18nKey,
  formatBenchmarkKindLabel,
  formatChatExperimentalPresetOptionLabel,
  formatClassifierFallbackNote,
  formatPresetSupportMessage,
  sanitizeLabPrimarySurfaceCopy,
} from "./product-copy";

const labT = (key: string) => {
  const map: Record<string, string> = {
    "benchmarkKindLabel.llm": "Chat model evaluation",
    "benchmarkKindLabel.ragPreset": "Retrieval evaluation",
    "benchmarkKindLabel.unknown": "Evaluation",
    "labConfigNotSingleTurn": "This preset is not available for this evaluation type.",
    "labConfigP13": "This preset is not available for this evaluation type. Use Chat for multi-turn flows.",
    "labConfigRequiresIndex": "A compatible index is required before running this evaluation.",
    "labConfigUnsupportedPreset": "This assistant configuration profile is not supported.",
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
    expect(formatBenchmarkKindLabel("RAG_PRESET_END_TO_END", labT)).toBe("Retrieval evaluation");
    expect(formatBenchmarkKindLabel("LLM_JUDGE_QA", labT)).toBe("Chat model evaluation");
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
    expect(label).toContain("Clarification loop");
    expect(label).not.toMatch(/^P13\b/);
    expect(label).not.toMatch(/REQUIRES_MULTI_TURN/);
    expect(label).not.toMatch(/PRESET_CLARIFICATION/);
    expect(label).toMatch(/not available for this evaluation type/i);
  });

  it("formatClassifierFallbackNote humanizes classifier fallback values", () => {
    expect(formatClassifierFallbackNote("UNAVAILABLE: timeout", labT)).toMatch(/unavailable/i);
    expect(formatClassifierFallbackNote("INVALID_OUTPUT", labT)).toMatch(/invalid output/i);
    expect(formatClassifierFallbackNote("INVALID_REQUEST: bad", labT)).toMatch(/invalid output/i);
    expect(formatClassifierFallbackNote("TIMEOUT waiting", labT)).toMatch(/unavailable/i);
    expect(formatClassifierFallbackNote(null, labT)).toBe("");
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
    expect(sanitizeLabPrimarySurfaceCopy("", "Knowledge base")).toBe("Knowledge base");
  });

  it("formatBenchmarkKindLabel covers embedding and classifier kinds", () => {
    const t = (key: string) =>
      ({
        "benchmarkKindLabel.embedding": "Embedding evaluation",
        "benchmarkKindLabel.classifier": "Classifier evaluation",
        "benchmarkKindLabel.unknown": "Evaluation",
      })[key] ?? key;
    expect(formatBenchmarkKindLabel("EMBEDDING_RETRIEVAL", t)).toBe("Embedding evaluation");
    expect(formatBenchmarkKindLabel("CLASSIFIER_METRICS", t)).toBe("Classifier evaluation");
  });

  it("formatChatExperimentalPresetOptionLabel returns base label when selectable", () => {
    const label = formatChatExperimentalPresetOptionLabel(
      {
        code: "P4",
        label: "Chunk metadata",
        supported: true,
        supportStatus: "EXECUTABLE",
        reasonIfUnsupported: null,
        requiresMultiTurn: false,
        chatSelectable: true,
      },
      labT,
    );
    expect(label).toBe("Chunk metadata");
  });

  it("formatPresetSupportMessage falls back for unsupported status without i18n", () => {
    const echo = (key: string) => key;
    expect(formatPresetSupportMessage("NOT_SUPPORTED", null, echo)).toBe("labConfigUnsupportedPreset");
  });

  it("benchmarkKindI18nKey normalizes benchmark enums", () => {
    expect(benchmarkKindI18nKey(" llm_judge_qa ")).toBe("benchmarkKindLabel.llm");
    expect(benchmarkKindI18nKey(undefined)).toBe("benchmarkKindLabel.unknown");
  });
});
