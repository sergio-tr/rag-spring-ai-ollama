import { describe, expect, it } from "vitest";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";
import {
  embeddingRowMatchesGoldFilter,
  embeddingRowMatchesHitFilter,
  embeddingRowMatchesQueryFilter,
  formatHitIndicator,
  recommendEmbeddingModel,
  sortEmbeddingComparisonRows,
  sortEmbeddingItemRows,
  toEmbeddingItemRow,
} from "@/features/lab/lib/embedding-result-table";

describe("embedding-result-table", () => {
  it("toEmbeddingItemRow prefers embedding model and retrieval fields over llm model", () => {
    const row = toEmbeddingItemRow(
      {
        itemId: "item-1",
        questionText: "Find heating notes",
        llmModelId: "qwen3.6:35b",
        embeddingModelId: "bge-m3",
        latencyMs: 180,
        metricsPayload: {
          gold_chunk_ids: ["ACTA_5_HEATING", "ACTA_5_RULES"],
          retrieved_chunk_ids: ["ACTA_5_HEATING", "ACTA_2_MISC"],
          recall_at_1: 1,
          recall_at_3: 1,
          recall_at_5: 1,
          first_relevant_rank: 1,
          mrr: 1,
          ndcg_at_5: 0.92,
          retrieved: [{ score: 0.41, chunk_id: "ACTA_5_HEATING", document_id: "ACTA_5" }],
        },
        mvp: {
          operational: {
            outcome: "EXECUTED",
            embeddingModelId: "bge-m3",
            modelId: "qwen3.6:35b",
            latencyMs: 180,
          },
          retrieval: {
            recallAt1: 1,
            recallAt3: 1,
            recallAt5: 1,
            mrr: 1,
          },
        },
      },
      0,
    );

    expect(row.embeddingModelId).toBe("bge-m3");
    expect(row.expectedGold).toContain("ACTA_5_HEATING");
    expect(row.retrievedTop1).toBe("ACTA_5_HEATING");
    expect(row.goldRank).toBe(1);
    expect(row.hitAt1).toBe(1);
    expect(row.topScore).toBeCloseTo(0.41);
  });

  it("embeddingRowMatchesHitFilter uses recall or rank", () => {
    const hit = toEmbeddingItemRow(
      {
        mvp: { operational: { outcome: "EXECUTED" }, retrieval: { recallAt1: 1 } },
        metricsPayload: { recall_at_1: 1, first_relevant_rank: 1 },
      },
      0,
    );
    const miss = toEmbeddingItemRow(
      {
        mvp: { operational: { outcome: "EXECUTED" }, retrieval: { recallAt1: 0 } },
        metricsPayload: { recall_at_1: 0, first_relevant_rank: 0 },
      },
      1,
    );
    expect(embeddingRowMatchesHitFilter(hit, "HIT")).toBe(true);
    expect(embeddingRowMatchesHitFilter(miss, "MISS")).toBe(true);
  });

  it("sortEmbeddingComparisonRows and recommendEmbeddingModel rank by retrieval quality", () => {
    const rows: ComparisonRow[] = [
      {
        embeddingModelId: "slow-best",
        comparisonLabel: "slow-best",
        executed: 10,
        meanRecallAt1: 0.5,
        meanMrr: 0.4,
        meanNdcgAt5: 0.3,
        meanLatencyMs: 900,
      },
      {
        embeddingModelId: "fast-good",
        comparisonLabel: "fast-good",
        executed: 10,
        meanRecallAt1: 0.45,
        meanMrr: 0.38,
        meanNdcgAt5: 0.28,
        meanLatencyMs: 120,
      },
      {
        embeddingModelId: "weak",
        comparisonLabel: "weak",
        executed: 10,
        meanRecallAt1: 0.1,
        meanMrr: 0.05,
        meanNdcgAt5: 0.02,
        meanLatencyMs: 80,
      },
    ];
    const sorted = sortEmbeddingComparisonRows(rows);
    expect(sorted[0]?.embeddingModelId).toBe("slow-best");
    const recommendation = recommendEmbeddingModel(rows);
    expect(recommendation?.bestModelId).toBe("slow-best");
    expect(recommendation?.latencyModelId).toBe("fast-good");
  });

  it("formatHitIndicator distinguishes hit, miss, and partial scores", () => {
    expect(formatHitIndicator(null)).toBe("Miss");
    expect(formatHitIndicator(0)).toBe("Miss");
    expect(formatHitIndicator(1)).toBe("Hit");
    expect(formatHitIndicator(0.75)).toBe("0.75");
  });

  it("embeddingRowMatchesGoldFilter and embeddingRowMatchesQueryFilter", () => {
    const row = toEmbeddingItemRow(
      {
        itemId: "q-42",
        questionText: "Find heating notes",
        mvp: { operational: { outcome: "EXECUTED" }, datasetQuestionId: "dq-1" },
        metricsPayload: { gold_chunk_ids: ["ACTA_5_HEATING"], retrieved_chunk_ids: ["ACTA_5_HEATING"] },
      },
      0,
    );
    expect(embeddingRowMatchesGoldFilter(row, "heating")).toBe(true);
    expect(embeddingRowMatchesGoldFilter(row, "")).toBe(true);
    expect(embeddingRowMatchesQueryFilter(row, "find")).toBe(true);
    expect(embeddingRowMatchesQueryFilter(row, "dq-1")).toBe(true);
    expect(embeddingRowMatchesQueryFilter(row, "missing")).toBe(false);
  });

  it("sortEmbeddingItemRows orders by hitAt1 then goldRank", () => {
    const low = toEmbeddingItemRow(
      { mvp: { operational: { outcome: "EXECUTED" }, retrieval: { recallAt1: 0 } }, metricsPayload: { recall_at_1: 0 } },
      0,
    );
    const high = toEmbeddingItemRow(
      {
        mvp: { operational: { outcome: "EXECUTED" }, retrieval: { recallAt1: 1, firstRelevantRank: 1 } },
        metricsPayload: { recall_at_1: 1, first_relevant_rank: 1 },
      },
      1,
    );
    const sorted = sortEmbeddingItemRows([low, high]);
    expect(sorted[0]?.hitAt1).toBe(1);
  });

  it("toEmbeddingItemRow reads JSON gold lists and document fallbacks", () => {
    const row = toEmbeddingItemRow(
      {
        status: "EXECUTED",
        metricsPayload: {
          gold_document_ids: '["DOC_A","DOC_B","DOC_C"]',
          retrieved_document_ids: "DOC_A;DOC_B",
          retrieved: [{ document_id: "DOC_A", score: 0.88 }],
        },
        mvp: {
          operational: { outcome: "EXECUTED" },
          retrieval: { goldFound: false },
        },
      },
      0,
    );
    expect(row.expectedGold).toContain("DOC_A");
    expect(row.retrievedTop1).toBe("DOC_A");
    expect(row.goldRank).toBe(0);
    expect(row.topScore).toBeCloseTo(0.88);
  });

  it("recommendEmbeddingModel returns null when no executed candidates", () => {
    expect(recommendEmbeddingModel([])).toBeNull();
    expect(
      recommendEmbeddingModel([
        { embeddingModelId: "x", comparisonLabel: "x", executed: 0, meanRecallAt1: 0.5 },
      ]),
    ).toBeNull();
  });

  it("recommendEmbeddingModel omits latency alternative when recall is too low", () => {
    const rows: ComparisonRow[] = [
      {
        embeddingModelId: "best",
        comparisonLabel: "best",
        executed: 10,
        meanRecallAt1: 0.8,
        meanMrr: 0.7,
        meanNdcgAt5: 0.6,
        meanLatencyMs: 900,
      },
      {
        embeddingModelId: "weak-fast",
        comparisonLabel: "weak-fast",
        executed: 10,
        meanRecallAt1: 0.1,
        meanMrr: 0.05,
        meanNdcgAt5: 0.02,
        meanLatencyMs: 50,
      },
    ];
    const recommendation = recommendEmbeddingModel(rows);
    expect(recommendation?.bestModelId).toBe("best");
    expect(recommendation?.latencyModelId).toBeNull();
  });
});
