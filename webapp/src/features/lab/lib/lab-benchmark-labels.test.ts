import { describe, expect, it } from "vitest";
import {
  MISSING_METADATA_KEY,
  aggregateComparisonOutcomeCounts,
  comparisonAxisForKind,
  formatComparisonScore,
  formatGroupLabel,
  formatMetricCell,
  formatOutcomeLabel,
  formatPresetDisplay,
  formatSupportStatusLabel,
  isExtensionPreset,
  isKnownOutcomeKey,
  isMissingMetadata,
  isNotAvailable,
  isPresetComparisonAxis,
  metricUnavailableReasonKey,
  normalizeMetadataKey,
  parseComparisonRows,
  resolveComparisonRowLabel,
  resolvePresetKeyFromComparisonRow,
  shouldShowPresetTrend,
  shouldShowTrendEmptyState,
  sortComparisonRows,
} from "./lab-benchmark-labels";

const t = (key: string) => key;

describe("lab-benchmark-labels product display", () => {
  it("formatPresetDisplay maps Demo system codes to product names", () => {
    expect(formatPresetDisplay("Demo_Best", "Demo_Best")).toBe("Production assistant configuration");
  });

  it("formatPresetDisplay uses functional preset names without P-code prefix", () => {
    expect(formatPresetDisplay("P4", "Hybrid retrieval")).toBe("Chunk retrieval with metadata");
  });

  it("normalizeMetadataKey maps empty and legacy unknown keys", () => {
    expect(normalizeMetadataKey("")).toBe(MISSING_METADATA_KEY);
    expect(normalizeMetadataKey("_UNKNOWN")).toBe(MISSING_METADATA_KEY);
    expect(normalizeMetadataKey("P4")).toBe("P4");
  });

  it("comparisonAxisForKind maps benchmark kinds", () => {
    expect(comparisonAxisForKind("LLM_JUDGE_QA")).toBe("LLM_MODEL");
    expect(comparisonAxisForKind("EMBEDDING_RETRIEVAL")).toBe("EMBEDDING_MODEL");
    expect(comparisonAxisForKind("RAG_PRESET_END_TO_END")).toBe("PRESET_CODE");
    expect(comparisonAxisForKind("OTHER")).toBe("UNKNOWN");
  });

  it("isExtensionPreset detects multi-turn extension presets", () => {
    expect(isExtensionPreset("P11")).toBe(true);
    expect(isExtensionPreset("P4")).toBe(false);
    expect(isMissingMetadata(null)).toBe(true);
  });

  it("formatGroupLabel handles preset and missing metadata", () => {
    expect(formatGroupLabel("presetCode", "P4", t)).toBe("Chunk retrieval with metadata");
    expect(formatGroupLabel("model", "", t)).toBe("benchmarkLabelMissingMetadata");
  });

  it("formatOutcomeLabel resolves known and unknown outcomes", () => {
    expect(formatOutcomeLabel("EXECUTED", t)).toBe("EXECUTED");
    expect(formatOutcomeLabel("CUSTOM", t)).toBe("CUSTOM");
    expect(isKnownOutcomeKey("FAILED")).toBe(true);
  });

  it("metricUnavailableReasonKey branches by metric and outcome", () => {
    expect(metricUnavailableReasonKey("recallAt1", "LLM_JUDGE_QA", "EXECUTED")).toBe(
      "benchmarkMetricReasonNoRetrieval",
    );
    expect(metricUnavailableReasonKey("recallAt1", "EMBEDDING_RETRIEVAL", "FAILED")).toBe(
      "benchmarkMetricReasonRunIncomplete",
    );
    expect(metricUnavailableReasonKey("llmJudgeScore", "RAG_PRESET_END_TO_END", "EXECUTED")).toBe(
      "benchmarkMetricReasonNoJudge",
    );
    expect(metricUnavailableReasonKey("latencyMs", "RAG_PRESET_END_TO_END", "SKIPPED")).toBe(
      "benchmarkMetricReasonSkipped",
    );
  });

  it("formatMetricCell formats numbers and unavailable values", () => {
    expect(formatMetricCell(0.4567, "recallAt1", "EMBEDDING_RETRIEVAL", "EXECUTED", t)).toEqual({
      display: "0.457",
      title: undefined,
    });
    expect(formatMetricCell(null, "recallAt1", "EMBEDDING_RETRIEVAL", "FAILED", t).display).toBe("-");
    expect(isNotAvailable("NOT_AVAILABLE")).toBe(true);
    expect(formatComparisonScore(null)).toBe("-");
    expect(formatComparisonScore(1.2)).toBe("1.200");
  });

  it("formatSupportStatusLabel maps internal status keys", () => {
    const translate = (key: string) => `label:${key}`;
    expect(formatSupportStatusLabel("SINGLE_TURN_SUPPORTED", translate)).toBe(
      "label:benchmarkSupportSingleTurnSupported",
    );
    expect(formatSupportStatusLabel("", t)).toBeNull();
    expect(formatSupportStatusLabel("UNKNOWN_STATUS", t)).toBeNull();
  });

  it("resolveComparisonRowLabel prefers stored labels and preset formatting", () => {
    expect(resolveComparisonRowLabel({ comparisonLabel: "Stored" }, "PRESET_CODE")).toBe("Stored");
    expect(
      resolveComparisonRowLabel({ presetKey: "P4", presetLabel: "Chunk metadata" }, "PRESET_CODE"),
    ).toContain("Chunk");
    expect(resolveComparisonRowLabel({ modelLabel: "llama" }, "LLM_MODEL")).toBe("llama");
    expect(isPresetComparisonAxis("PRESET")).toBe(true);
    expect(resolvePresetKeyFromComparisonRow({ presetKey: "P8" })).toBe("P8");
    expect(resolvePresetKeyFromComparisonRow({ presetLabel: "P14 memory" })).toBe("P14");
  });

  it("aggregates, parses, and sorts comparison rows", () => {
    const rows = [
      { executed: 2, failed: 1, meanExactMatch: 0.2 },
      { executed: 1, skipped: 3, meanExactMatch: 0.8 },
    ];
    expect(aggregateComparisonOutcomeCounts(rows)).toEqual({
      EXECUTED: 3,
      FAILED: 1,
      SKIPPED: 3,
    });
    const parsed = parseComparisonRows({
      comparisonAxis: "PRESET_CODE",
      rows: [{ presetKey: "P4", presetLabel: "Chunk metadata", executed: 1 }],
    });
    expect(parsed[0]?.presetKey).toBe("P4");
    expect(parsed[0]?.comparisonLabel).toBeTruthy();
    expect(sortComparisonRows(rows)[0]?.meanExactMatch).toBe(0.8);
    expect(parseComparisonRows(null)).toEqual([]);
  });

  it("trend helpers gate preset charts", () => {
    expect(shouldShowPresetTrend("RAG_PRESET_END_TO_END", 2)).toBe(true);
    expect(shouldShowPresetTrend("LLM_JUDGE_QA", 2)).toBe(false);
    expect(shouldShowTrendEmptyState("RAG_PRESET_END_TO_END", true, 0)).toBe(true);
    expect(shouldShowTrendEmptyState("RAG_PRESET_END_TO_END", false, 0)).toBe(false);
  });

  it("formatPresetDisplay handles missing metadata and raw codes", () => {
    expect(formatPresetDisplay(null, null)).toBe("");
    expect(formatPresetDisplay("P6", "P6")).toBe("Metadata query intelligence");
    expect(formatPresetDisplay("P6", "Custom label")).toBe("Metadata query intelligence");
  });

  it("formatGroupLabel returns normalized values for non-preset groups", () => {
    expect(formatGroupLabel("llmModelId", "llama3.1", t)).toBe("llama3.1");
  });

  it("formatMetricCell handles string values and empty text", () => {
    expect(formatMetricCell("0.812", "latencyMs", "LLM_JUDGE_QA", "EXECUTED", t)).toEqual({
      display: "0.812",
      title: undefined,
    });
    expect(formatMetricCell("NOT_AVAILABLE", "latencyMs", "LLM_JUDGE_QA", "EXECUTED", t).display).toBe("-");
    expect(metricUnavailableReasonKey("latencyMs", "RAG_PRESET_END_TO_END", "FAILED")).toBe(
      "benchmarkMetricReasonRunFailed",
    );
  });

  it("resolveComparisonRowLabel keeps em dash labels and axis fallbacks", () => {
    expect(
      resolveComparisonRowLabel({ presetLabel: "P4 - Chunk metadata" }, "PRESET_CODE"),
    ).toBe("P4 - Chunk metadata");
    expect(resolveComparisonRowLabel({ axisValue: "axis-1" }, "LLM_MODEL")).toBe("axis-1");
    expect(resolvePresetKeyFromComparisonRow({ groupValue: "P11" })).toBe("P11");
  });

  it("parseComparisonRows ignores invalid payloads and non-object rows", () => {
    expect(parseComparisonRows({ comparisonAxis: "PRESET", rows: [null, "bad"] })).toEqual([]);
    expect(parseComparisonRows({ rows: [{ presetKey: "P2", executed: "nope" }] })[0]?.executed).toBe("nope");
    expect(aggregateComparisonOutcomeCounts([{ executed: 0, failed: -1 }])).toEqual({});
  });

  it("formatComparisonScore and formatPresetDisplay cover remaining branches", () => {
    expect(formatComparisonScore("NOT_AVAILABLE")).toBe("-");
    expect(formatComparisonScore("plain")).toBe("plain");
    expect(formatPresetDisplay("demo_worst", "demo_worst")).toBe("Basic baseline configuration");
    expect(
      formatMetricCell("", "recallAt1", "EMBEDDING_RETRIEVAL", "EXECUTED", t).display,
    ).toBe("-");
  });
});
