import { describe, expect, it } from "vitest";
import {
  comparisonAxisForKind,
  formatGroupLabel,
  formatMetricCell,
  formatOutcomeLabel,
  formatPresetDisplay,
  isExtensionPreset,
  isMissingMetadata,
  isNotAvailable,
  metricUnavailableReasonKey,
  normalizeMetadataKey,
  parseComparisonRows,
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
    expect(formatGroupLabel("embeddingModelId", "bge-m3", t)).toBe("bge-m3");
    expect(formatGroupLabel("presetCode", "", t)).toBe("benchmarkLabelMissingMetadata");
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

  it("covers comparison axis default and preset display edge cases", () => {
    expect(comparisonAxisForKind("OTHER")).toBe("UNKNOWN");
    expect(formatPresetDisplay("_UNKNOWN", "x")).toBe("");
    expect(formatPresetDisplay("P2", "P2")).toBe("P2");
    expect(isExtensionPreset("P14")).toBe(true);
    expect(formatGroupLabel("presetCode", "P3", t)).toBe("P3");
    expect(formatOutcomeLabel("CUSTOM", t)).toBe("CUSTOM");
    expect(formatOutcomeLabel("FAILED", t)).toBe("benchmarkOutcomeLabel.FAILED");
    expect(isNotAvailable("NOT_AVAILABLE")).toBe(true);
  });

  it("covers metric unavailable reason branches", () => {
    expect(metricUnavailableReasonKey("recallAt1", "EMBEDDING_RETRIEVAL", "FAILED")).toBe(
      "benchmarkMetricReasonRunIncomplete",
    );
    expect(metricUnavailableReasonKey("recallAt1", "RAG_PRESET_END_TO_END", "EXECUTED")).toBe(
      "benchmarkMetricReasonRetrievalNa",
    );
    expect(metricUnavailableReasonKey("llmJudgeScore", "RAG_PRESET_END_TO_END", "EXECUTED")).toBe(
      "benchmarkMetricReasonNoJudge",
    );
    expect(metricUnavailableReasonKey("meanExactMatch", "LLM_JUDGE_QA", "FAILED")).toBe(
      "benchmarkMetricReasonRunFailed",
    );
    expect(metricUnavailableReasonKey("meanExactMatch", "LLM_JUDGE_QA", "SKIPPED")).toBe(
      "benchmarkMetricReasonSkipped",
    );
    expect(metricUnavailableReasonKey("meanExactMatch", "LLM_JUDGE_QA", "NOT_SUPPORTED")).toBe(
      "benchmarkMetricReasonSkipped",
    );
    expect(metricUnavailableReasonKey("meanExactMatch", "LLM_JUDGE_QA", "EXECUTED")).toBe(
      "benchmarkMetricReasonGeneric",
    );
    expect(metricUnavailableReasonKey("recallAt1", "LLM_JUDGE_QA", "EXECUTED")).toBe(
      "benchmarkMetricReasonNoRetrieval",
    );
  });

  it("formats numeric and string metric cells", () => {
    expect(formatMetricCell(0.8123, "meanExactMatch", "LLM_JUDGE_QA", "EXECUTED", t).display).toBe("0.812");
    expect(formatMetricCell("0.55", "meanExactMatch", "LLM_JUDGE_QA", "EXECUTED", t).display).toBe("0.55");
    expect(formatMetricCell("  ", "meanExactMatch", "LLM_JUDGE_QA", "EXECUTED", t).display).toBe("—");
  });

  it("parses comparison rows from campaign payload shapes", () => {
    expect(parseComparisonRows(null)).toEqual([]);
    expect(parseComparisonRows({ rows: "bad" })).toEqual([]);
    const rows = parseComparisonRows({
      rows: [
        { modelLabel: "llama3", meanExactMatch: 0.5 },
        { presetLabel: "P2 — Baseline", axisValue: "P2", meanExactMatch: 0.7 },
        { comparisonLabel: "custom", meanExactMatch: 0.1 },
      ],
    });
    expect(rows[0]?.comparisonLabel).toBe("llama3");
    expect(rows[1]?.comparisonLabel).toBe("P2 — Baseline");
    expect(rows[2]?.comparisonLabel).toBe("custom");
  });
});
