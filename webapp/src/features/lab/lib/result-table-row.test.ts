import { describe, expect, it } from "vitest";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";
import {
  sortComparisonRowsByKey,
  sortEmbeddingPerItemRows,
  sortResultTableRows,
  toResultTableRow,
  truncateTableCell,
  type ResultTableRow,
} from "@/features/lab/lib/result-table-row";
import type { EmbeddingItemRow } from "@/features/lab/lib/embedding-result-table";

const t = (key: string) => key;

describe("toResultTableRow", () => {
  it("maps expectedAnswer, actualAnswer and contextText from API fields", () => {
    const row = toResultTableRow(
      {
        itemId: "i1",
        questionText: "What is RAG?",
        expectedAnswer: "Retrieval augmented generation",
        actualAnswer: "RAG combines retrieval and generation",
        contextText: "Oracle context paragraph",
        mvp: {
          operational: { outcome: "EXECUTED", modelId: "gpt-4" },
          generation: { correctness: 0.8 },
        },
      },
      0,
      t,
    );
    expect(row.expectedAnswer).toBe("Retrieval augmented generation");
    expect(row.actualAnswer).toBe("RAG combines retrieval and generation");
    expect(row.contextText).toBe("Oracle context paragraph");
  });

  it("reads context_text alias from metricsPayload", () => {
    const row = toResultTableRow(
      {
        question: "Q",
        actualAnswer: "A",
        metricsPayload: { context_text: "Payload context" },
        mvp: { operational: { outcome: "EXECUTED" } },
      },
      0,
      t,
    );
    expect(row.contextText).toBe("Payload context");
  });

  it("maps SKIPPED outcome with skip reason and technical detail", () => {
    const row = toResultTableRow(
      {
        mvp: {
          operational: {
            outcome: "SKIPPED",
            skipReasonCode: "NO_CONTEXT",
            skipReason: "No context available",
          },
        },
      },
      1,
      t,
    );
    expect(row.outcome).toBe("SKIPPED");
    expect(row.technicalDetail).toBe("NO_CONTEXT");
  });

  it("maps FAILED and NOT_SUPPORTED outcomes", () => {
    const failed = toResultTableRow(
      { mvp: { operational: { outcome: "FAILED" } } },
      0,
      t,
    );
    expect(failed.note).toBe("benchmarkNoteSeeExport");

    const unsupported = toResultTableRow(
      {
        mvp: {
          operational: { outcome: "NOT_SUPPORTED", unsupportedReason: "MODEL_UNAVAILABLE" },
        },
      },
      0,
      t,
    );
    expect(unsupported.outcome).toBe("NOT_SUPPORTED");
    expect(unsupported.note).toContain("model");
  });

  it("uses extension preset note for P11", () => {
    const row = toResultTableRow(
      {
        presetCode: "P11",
        mvp: { operational: { outcome: "EXECUTED", presetCode: "P11" } },
      },
      0,
      t,
    );
    expect(row.note).toBe("benchmarkNoteExtension");
  });

  it("summarizes sources from array, string, and payload fields", () => {
    const fromArray = toResultTableRow(
      { sources: [{ id: "a" }, { id: "b" }], mvp: { operational: { outcome: "EXECUTED" } } },
      0,
      t,
    );
    expect(fromArray.sourcesSummary).toBe("2 source(s)");

    const fromDocString = toResultTableRow(
      {
        metricsPayload: { retrieved_document_ids: "doc1;doc2;doc3" },
        mvp: { operational: { outcome: "EXECUTED" } },
      },
      0,
      t,
    );
    expect(fromDocString.sourcesSummary).toBe("3 doc(s)");

    const fromChunkString = toResultTableRow(
      {
        metricsPayload: { retrieved_chunk_ids: "c1;c2" },
        mvp: { operational: { outcome: "EXECUTED" } },
      },
      0,
      t,
    );
    expect(fromChunkString.sourcesSummary).toBe("2 chunk(s)");

    const fromChunkArray = toResultTableRow(
      {
        metricsPayload: { retrieved_chunk_ids: ["c1", "c2", "c3"] },
        mvp: { operational: { outcome: "EXECUTED" } },
      },
      0,
      t,
    );
    expect(fromChunkArray.sourcesSummary).toBe("3 chunk(s)");

    const fromDocArray = toResultTableRow(
      {
        metricsPayload: { retrieved_document_ids: ["d1", "d2"] },
        mvp: { operational: { outcome: "EXECUTED" } },
      },
      0,
      t,
    );
    expect(fromDocArray.sourcesSummary).toBe("2 doc(s)");
  });

  it("resolves model, preset, question, id, and snapshot fallbacks", () => {
    const row = toResultTableRow(
      {
        id: "fallback-id",
        question: "From question field",
        embeddingModelId: "embed-1",
        mvp: {
          operational: { outcome: "EXECUTED", presetCode: "P4", modelId: "llm-1" },
          generation: {
            expectedAnswer: "gen expected",
            actualAnswer: "gen actual",
            correctness: 0.5,
            llmJudgeScore: 0.6,
            hallucinationRate: 0.1,
            faithfulness: 0.9,
            sourceSupport: 0.8,
            dateCorrectness: 1,
          },
        },
        metricsPayload: { indexSnapshotId: "snap-99", presetLabel: "Preset Four" },
      },
      3,
      t,
    );
    expect(row.id).toBe("fallback-id");
    expect(row.question).toBe("From question field");
    expect(row.modelId).toBe("llm-1");
    expect(row.presetCode).toBe("P4");
    expect(row.snapshotId).toBe("snap-99");
    expect(row.correctness).toBe(0.5);
    expect(row.llmJudgeScore).toBe(0.6);
  });

  it("derives skip reason from failureReason when operational skip fields are absent", () => {
    const row = toResultTableRow(
      {
        failureReason: "TIMEOUT: request timed out",
        mvp: { operational: { outcome: "SKIPPED" } },
      },
      0,
      t,
    );
    expect(row.outcome).toBe("SKIPPED");
    expect(row.technicalDetail).toContain("TIMEOUT");
  });
});

describe("sortResultTableRows", () => {
  const baseRows: ResultTableRow[] = [
    {
      id: "a",
      question: "Alpha",
      expectedAnswer: "",
      actualAnswer: "",
      contextText: "",
      outcome: "EXECUTED",
      note: "-",
      technicalDetail: "",
      presetCode: "P1",
      presetLabel: "Preset A",
      modelId: "m1",
      snapshotId: "-",
      sourcesSummary: "-",
      correctness: 0.2,
      llmJudgeScore: null,
      hallucinationRate: null,
      faithfulness: null,
      sourceSupport: null,
      dateCorrectness: null,
      derivedErrorClass: null,
    },
    {
      id: "b",
      question: "Beta",
      expectedAnswer: "",
      actualAnswer: "",
      contextText: "",
      outcome: "FAILED",
      note: "-",
      technicalDetail: "",
      presetCode: "P2",
      presetLabel: "Preset B",
      modelId: "m2",
      snapshotId: "-",
      sourcesSummary: "-",
      correctness: 0.9,
      llmJudgeScore: null,
      hallucinationRate: null,
      faithfulness: null,
      sourceSupport: null,
      dateCorrectness: null,
      derivedErrorClass: null,
    },
  ];

  it("defaults to correctness desc when sort is null", () => {
    const sorted = sortResultTableRows(baseRows, null);
    expect(sorted.map((r) => r.id)).toEqual(["b", "a"]);
  });

  it("sorts by question and outcome keys", () => {
    const byQuestion = sortResultTableRows(baseRows, { key: "question", direction: "asc" });
    expect(byQuestion.map((r) => r.id)).toEqual(["a", "b"]);
    const byOutcome = sortResultTableRows(baseRows, { key: "outcome", direction: "desc" });
    expect(byOutcome[0]?.id).toBe("b");
  });
});

describe("sortEmbeddingPerItemRows", () => {
  const rows: EmbeddingItemRow[] = [
    {
      id: "a",
      question: "Q1",
      datasetQuestionId: "",
      embeddingModelId: "e1",
      expectedGold: "g1",
      retrievedTop1: "r1",
      goldRank: 3,
      hitAt1: 0,
      hitAt3: null,
      hitAt5: null,
      topScore: null,
      latencyMs: null,
      outcome: "EXECUTED",
      answer: "",
      answerModelId: "",
      hasDownstreamAnswer: false,
    },
    {
      id: "b",
      question: "Q2",
      datasetQuestionId: "",
      embeddingModelId: "e2",
      expectedGold: "g2",
      retrievedTop1: "r2",
      goldRank: 1,
      hitAt1: 1,
      hitAt3: null,
      hitAt5: null,
      topScore: null,
      latencyMs: null,
      outcome: "EXECUTED",
      answer: "",
      answerModelId: "",
      hasDownstreamAnswer: false,
    },
  ];

  it("defaults to hitAt1 desc then goldRank asc", () => {
    const sorted = sortEmbeddingPerItemRows(rows, null);
    expect(sorted.map((r) => r.id)).toEqual(["b", "a"]);
  });

  it("sorts by goldRank when requested", () => {
    const sorted = sortEmbeddingPerItemRows(rows, { key: "goldRank", direction: "asc" });
    expect(sorted.map((r) => r.id)).toEqual(["b", "a"]);
  });
});

describe("sortComparisonRowsByKey", () => {
  const rows: ComparisonRow[] = [
    {
      llmModelId: "slow",
      comparisonLabel: "slow",
      executed: 5,
      totalItems: 10,
      meanCorrectness: 0.9,
      meanLatencyMs: 500,
      meanRecallAt1: 0.8,
    },
    {
      llmModelId: "fast",
      comparisonLabel: "fast",
      executed: 8,
      totalItems: 10,
      meanCorrectness: 0.7,
      meanLatencyMs: 100,
      meanRecallAt1: 0.6,
    },
  ];

  it("defaults to meanCorrectness desc", () => {
    const sorted = sortComparisonRowsByKey(rows, null, "LLM_MODEL");
    expect(sorted[0]?.llmModelId).toBe("slow");
  });

  it("sorts by meanLatency and coverage", () => {
    const byLatency = sortComparisonRowsByKey(rows, { key: "meanLatency", direction: "asc" }, "LLM_MODEL");
    expect(byLatency[0]?.llmModelId).toBe("fast");
    const byCoverage = sortComparisonRowsByKey(rows, { key: "coverage", direction: "desc" }, "LLM_MODEL");
    expect(byCoverage[0]?.llmModelId).toBe("fast");
  });
});

describe("truncateTableCell", () => {
  it("returns em dash for blank values and truncates long text", () => {
    expect(truncateTableCell("  ")).toEqual({ display: "-", full: "" });
    expect(truncateTableCell("short")).toEqual({ display: "short", full: "short" });
    const long = "a".repeat(80);
    const truncated = truncateTableCell(long, 10);
    expect(truncated.display).toBe(`${"a".repeat(10)}…`);
    expect(truncated.full).toBe(long);
  });
});
