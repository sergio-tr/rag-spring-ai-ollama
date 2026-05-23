import { describe, expect, it } from "vitest";
import {
  comparisonAxisForKind,
  formatGroupLabel,
  formatMetricCell,
  formatPresetDisplay,
  isExtensionPreset,
  isMissingMetadata,
  normalizeMetadataKey,
  shouldShowPresetTrend,
  shouldShowTrendEmptyState,
  sortComparisonRows,
} from "./lab-benchmark-labels";

const t = (key: string) => key;

describe("lab-benchmark-labels", () => {
  it("normalizes legacy unknown keys", () => {
    expect(normalizeMetadataKey("_UNKNOWN")).toBe("MISSING_METADATA");
    expect(normalizeMetadataKey("")).toBe("MISSING_METADATA");
    expect(normalizeMetadataKey("llama3")).toBe("llama3");
    expect(isMissingMetadata("_UNKNOWN")).toBe(true);
  });

  it("maps benchmark kinds to comparison axes", () => {
    expect(comparisonAxisForKind("LLM_JUDGE_QA")).toBe("LLM_MODEL");
    expect(comparisonAxisForKind("EMBEDDING_RETRIEVAL")).toBe("EMBEDDING_MODEL");
    expect(comparisonAxisForKind("RAG_PRESET_END_TO_END")).toBe("PRESET_CODE");
  });

  it("formats preset labels and extension presets", () => {
    expect(formatPresetDisplay("P2", "Baseline RAG")).toBe("P2 — Baseline RAG");
    expect(isExtensionPreset("P13")).toBe(true);
    expect(isExtensionPreset("P2")).toBe(false);
  });

  it("formats missing metadata group labels", () => {
    expect(formatGroupLabel("modelId", "_UNKNOWN", t)).toBe("benchmarkLabelMissingMetadata");
    expect(formatGroupLabel("presetCode", "P1", t)).toBe("P1");
  });

  it("formats NOT_AVAILABLE metrics with reason tooltips", () => {
    const cell = formatMetricCell("NOT_AVAILABLE", "recallAt1", "LLM_JUDGE_QA", "EXECUTED", t);
    expect(cell.display).toBe("—");
    expect(cell.title).toBe("benchmarkMetricReasonNoRetrieval");
  });

  it("sorts comparison rows by mean exact match descending", () => {
    const sorted = sortComparisonRows([
      { comparisonLabel: "m1", meanExactMatch: 0.2 },
      { comparisonLabel: "m2", meanExactMatch: 0.9 },
      { comparisonLabel: "m3", meanExactMatch: null },
    ]);
    expect(sorted.map((r) => r.comparisonLabel)).toEqual(["m2", "m1", "m3"]);
  });

  it("decides trend visibility for RAG preset benchmarks only", () => {
    expect(shouldShowPresetTrend("RAG_PRESET_END_TO_END", 2)).toBe(true);
    expect(shouldShowPresetTrend("LLM_JUDGE_QA", 2)).toBe(false);
    expect(shouldShowTrendEmptyState("RAG_PRESET_END_TO_END", true, 0)).toBe(true);
    expect(shouldShowTrendEmptyState("LLM_JUDGE_QA", true, 0)).toBe(false);
  });
});
