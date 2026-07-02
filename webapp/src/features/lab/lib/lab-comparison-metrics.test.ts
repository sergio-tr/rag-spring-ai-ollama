import { describe, expect, it } from "vitest";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";
import {
  aggregateRunMetricsFromItems,
  enrichComparisonRowsFromItems,
  formatLatencyMs,
  formatMetricNumber,
  formatRatioPercent,
  mergeComparisonRowWithEnrichment,
} from "@/features/lab/lib/lab-comparison-metrics";

describe("lab-comparison-metrics", () => {
  const runId = "run-abc";

  it("aggregateRunMetricsFromItems filters by run id and aggregates metrics", () => {
    const items = [
      {
        childRunId: runId,
        actualAnswer: "answer",
        mvp: {
          operational: { outcome: "EXECUTED", latencyMs: 100 },
          retrieval: { recallAt1: 1, recallAt3: 0.8, recallAt5: 0.6, mrr: 0.9 },
          analysis: { ndcgAt5: 0.7 },
          generation: {
            correctness: 0.85,
            faithfulness: 0.9,
            hallucinationRate: 0.1,
            semanticScore: 0.75,
            containsExpectedAnswer: 1,
          },
        },
      },
      {
        runId: "other-run",
        mvp: { operational: { outcome: "EXECUTED", latencyMs: 50 } },
      },
      {
        mvp: {
          operational: {
            runId,
            outcome: "FAILED",
            failureCode: "TIMEOUT",
            latencyMs: 200,
          },
          analysis: { sourceCoverageStatus: "NO_CONTEXT" },
        },
        answer: "",
      },
      {
        childRunId: runId,
        actualAnswer: "   ",
        mvp: {
          operational: { outcome: "EXECUTED", latencyMs: 50 },
        },
      },
      null,
      "bad",
    ];

    const out = aggregateRunMetricsFromItems(items, runId);
    expect(out.meanRecallAt1).toBe(1);
    expect(out.meanRecallAt3).toBe(0.8);
    expect(out.meanRecallAt5).toBe(0.6);
    expect(out.meanMrr).toBe(0.9);
    expect(out.meanNdcgAt5).toBe(0.7);
    expect(out.meanCorrectness).toBe(0.85);
    expect(out.meanFaithfulness).toBe(0.9);
    expect(out.meanHallucinationRate).toBe(0.1);
    expect(out.meanSemanticScore).toBe(0.75);
    expect(out.containsExpectedAnswerRate).toBe(1);
    expect(out.meanLatencyMs).toBeCloseTo(116.67, 2);
    expect(out.p95LatencyMs).toBe(200);
    expect(out.errorCount).toBe(1);
    expect(out.timeoutCount).toBe(1);
    expect(out.emptyContentCount).toBe(1);
    expect(out.noContextCount).toBe(1);
  });

  it("aggregateRunMetricsFromItems reads run id from operational and status fallbacks", () => {
    const items = [
      {
        status: "EXECUTED",
        mvp: {
          operational: { runId, skipReasonCode: "SKIP_TIMEOUT" },
          analysis: { recallAt1: "NOT_AVAILABLE", retrievalCoverageStatus: "NO_CONTEXT" },
          generation: { llmJudgeScore: 0.5, containsExpectedAnswer: false },
        },
      },
      {
        mvp: {
          operational: { outcome: "EXECUTED" },
        },
        answer: "fallback answer",
      },
    ];
    const out = aggregateRunMetricsFromItems(items, runId);
    expect(out.timeoutCount).toBe(1);
    expect(out.noContextCount).toBe(1);
    expect(out.meanSemanticScore).toBe(0.5);
    expect(out.meanRecallAt1).toBeNull();
    expect(out.containsExpectedAnswerRate).toBe(0);
  });

  it("mergeComparisonRowWithEnrichment fills missing row metrics from enrichment", () => {
    const row: ComparisonRow = {
      runId,
      comparisonLabel: "Model B",
    };
    const merged = mergeComparisonRowWithEnrichment(row, {
      meanRecallAt3: 0.55,
      meanFaithfulness: 0.8,
      timeoutCount: 1,
    });
    expect(merged.meanRecallAt3).toBe(0.55);
    expect(merged.meanFaithfulness).toBe(0.8);
    expect(merged.timeoutCount).toBe(1);
    expect(merged.errorCount).toBe(0);
  });

  it("mergeComparisonRowWithEnrichment preserves row values when present", () => {
    const row: ComparisonRow = {
      runId,
      comparisonLabel: "Model A",
      meanRecallAt1: 0.42,
      meanExactMatch: 0.5,
      meanSemanticScore: 0.6,
      meanLatencyMs: 120,
    };
    const merged = mergeComparisonRowWithEnrichment(row, {
      meanRecallAt1: 0.9,
      meanRecallAt3: 0.8,
      meanCorrectness: 0.7,
      meanSemanticScore: 0.1,
      meanLatencyMs: 10,
      p95LatencyMs: 250,
      errorCount: 2,
    });
    expect(merged.meanRecallAt1).toBe(0.42);
    expect(merged.meanRecallAt3).toBe(0.8);
    expect(merged.meanExactMatch).toBe(0.5);
    expect(merged.meanSemanticScore).toBe(0.6);
    expect(merged.meanLatencyMs).toBe(120);
    expect(merged.p95LatencyMs).toBe(250);
    expect(merged.errorCount).toBe(2);
  });

  it("enrichComparisonRowsFromItems skips rows without run id", () => {
    const rows: ComparisonRow[] = [
      { runId: "", comparisonLabel: "empty" },
      { runId: runId, comparisonLabel: "filled" },
    ];
    const items = [
      {
        runId,
        mvp: {
          operational: { outcome: "EXECUTED", latencyMs: 40 },
          generation: { correctness: 1 },
        },
      },
    ];
    const enriched = enrichComparisonRowsFromItems(rows, items);
    expect(enriched[0]).toBe(rows[0]);
    expect(enriched[1]?.meanCorrectness).toBe(1);
    expect(enriched[1]?.meanLatencyMs).toBe(40);
  });

  it("format helpers handle empty and invalid values", () => {
    expect(formatRatioPercent(1, 0)).toBe("—");
    expect(formatRatioPercent(1, 4)).toBe("25.0%");
    expect(formatMetricNumber(null)).toBe("—");
    expect(formatMetricNumber("NOT_AVAILABLE")).toBe("—");
    expect(formatMetricNumber(0.12345, 2)).toBe("0.12");
    expect(formatMetricNumber("  ok ")).toBe("ok");
    expect(formatMetricNumber("   ")).toBe("—");
    expect(formatLatencyMs(null)).toBe("—");
    expect(formatLatencyMs(12.6)).toBe("13");
    expect(formatLatencyMs("x")).toBe("—");
  });
});
