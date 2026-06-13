import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { LabBenchmarkResultsPanel } from "./lab-benchmark-results-panel";

vi.mock("@/features/lab/lib/lab-benchmark-results-api", () => ({
  fetchLabEvaluationRun: vi.fn(),
  fetchLabCampaignRuns: vi.fn(),
  fetchCampaignComparison: vi.fn(),
  fetchCampaignItemsBundle: vi.fn(),
  fetchMvpRollupsJson: vi.fn(),
  fetchMvpItemsBundle: vi.fn(),
  downloadMvpExport: vi.fn(),
  downloadCampaignMvpItemsJson: vi.fn(),
  downloadCampaignItemsJson: vi.fn(),
  downloadCampaignSummaryJson: vi.fn(),
  downloadCampaignExport: vi.fn(),
}));

import {
  fetchLabEvaluationRun,
  fetchLabCampaignRuns,
  fetchCampaignComparison,
  fetchCampaignItemsBundle,
  fetchMvpItemsBundle,
  fetchMvpRollupsJson,
} from "@/features/lab/lib/lab-benchmark-results-api";
import { ApiError } from "@/lib/api-client";

const baseRun = {
  id: "550e8400-e29b-41d4-a716-446655440099",
  name: null,
  status: "SUCCEEDED",
  benchmarkKind: "LLM_JUDGE_QA",
  runKind: null,
  workflowSchemaVersion: null,
  datasetSha256: null,
  datasetId: null,
  asyncTaskId: null,
  resolvedConfigSnapshotId: null,
  indexSnapshotId: null,
  indexSignatureHash: null,
  presetId: null,
  llmModelId: null,
  embeddingModelId: null,
  classifierModelId: null,
  aggregatesJson: null,
  createdAt: "",
  completedAt: null,
};

function renderPanel(props: { evaluationRunId: string; campaignId?: string; loadEnabled?: boolean }) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <IntlTestProvider>
        <LabBenchmarkResultsPanel
          evaluationRunId={props.evaluationRunId}
          campaignId={props.campaignId}
          loadEnabled={props.loadEnabled ?? true}
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("LabBenchmarkResultsPanel", () => {
  beforeEach(() => {
    vi.mocked(fetchLabEvaluationRun).mockReset();
    vi.mocked(fetchLabCampaignRuns).mockReset();
    vi.mocked(fetchMvpRollupsJson).mockReset();
    vi.mocked(fetchMvpItemsBundle).mockReset();
    vi.mocked(fetchCampaignComparison).mockReset();
    vi.mocked(fetchCampaignItemsBundle).mockReset();
    vi.mocked(fetchCampaignItemsBundle).mockResolvedValue({ items: [] });
  });

  it("resolves campaignId from run detail when prop is missing", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
      campaignId: "recovered-campaign-id",
      campaignMode: true,
      campaignPersistedItemCount: 240,
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 240 }, onExecuted: { n: 240, meanNormalizedExactMatch: 0.5 } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({
      items: [
        {
          itemId: "item-1",
          question: "Q1",
          presetCode: "P0",
          presetLabel: "Corpus text only",
          status: "EXECUTED",
          mvp: { operational: { outcome: "EXECUTED", presetCode: "P0" } },
        },
      ],
    });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "PRESET_CODE",
      rows: [
        {
          presetKey: "P0",
          presetLabel: "Corpus text only",
          executed: 60,
          skipped: 0,
          totalItems: 60,
          scoreAnswerable: 0.91,
          benchmarkSupportStatus: "SINGLE_TURN_SUPPORTED",
        },
        {
          presetKey: "P1",
          presetLabel: "Dense retrieval",
          executed: 60,
          skipped: 0,
          totalItems: 60,
          scoreAnswerable: 0.82,
        },
      ],
    } as never);

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByTestId("lab-export-campaign-items-json")).toBeInTheDocument());
    await waitFor(() => expect(screen.getByTestId("lab-campaign-comparison-panel")).toBeInTheDocument());
    expect(fetchCampaignComparison).toHaveBeenCalledWith("recovered-campaign-id");
    expect(screen.getByText("Answerable score")).toBeInTheDocument();
    expect(screen.getByText("0.910")).toBeInTheDocument();
    expect(screen.queryByTestId("lab-benchmark-no-executed-warning")).not.toBeInTheDocument();
  });

  it("loads rollups and shows outcome badges when enabled", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: {
        outcomeCounts: { EXECUTED: 1, NOT_SUPPORTED: 1 },
        onExecuted: { n: 1, meanNormalizedExactMatch: 0.75 },
      },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({
      items: [
        {
          id: "item-1",
          questionText: "hello?",
          mvp: {
            datasetQuestionId: "RAG-001",
            generation: {
              correctness: 0.8,
              llmJudgeScore: 0.7,
              hallucinationRate: 0.1,
              dateCorrectness: 1,
            },
            operational: { outcome: "NOT_SUPPORTED", unsupportedReason: "NOPE", presetCode: "P13", modelId: "m1" },
          },
        },
        {
          id: "item-2",
          questionText: "other?",
          mvp: {
            datasetQuestionId: "RAG-002",
            generation: { correctness: 0.4, llmJudgeScore: 0.5, hallucinationRate: 0.2, dateCorrectness: null },
            operational: { outcome: "EXECUTED", presetCode: "P2", modelId: "m2" },
          },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByTestId("lab-outcome-NOT_SUPPORTED")).toBeInTheDocument());
    expect(screen.getByTestId("lab-outcome-EXECUTED")).toHaveTextContent(/1/i);
    expect(screen.getByTestId("lab-benchmark-results-counts")).toBeInTheDocument();
    expect(screen.getByTestId("lab-benchmark-trend-graph")).toHaveTextContent(/P13/i);
    expect(screen.getByTestId("lab-extension-legend")).toBeInTheDocument();
    expect(screen.getByTestId("lab-failed-skipped-section")).toBeInTheDocument();

    fireEvent.change(screen.getByTestId("lab-results-filter-model"), { target: { value: "m2" } });
    expect(screen.queryByText(/hello\?/i)).not.toBeInTheDocument();
    expect(screen.getByText(/other\?/i)).toBeInTheDocument();
  });

  it("shows friendly error when MVP bundle fetch fails", async () => {
    vi.mocked(fetchLabEvaluationRun).mockRejectedValueOnce(new ApiError(403, "denied", { kind: "http" }));

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByTestId("lab-benchmark-results-error")).toBeInTheDocument());
    expect(screen.getByRole("alert")).toHaveTextContent(/denied/i);
  });

  it("renders unknown outcome keys from rollup counts", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue(baseRun);
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: {
        outcomeCounts: { EXECUTED: 1, CUSTOM_LABEL: 2 },
        onExecuted: { n: 0, meanNormalizedExactMatch: null },
      },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByText(/Unknown outcome: 2/i)).toBeInTheDocument());
  });

  it("hides trend graph for LLM benchmarks and shows no empty trend copy", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue(baseRun);
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 0 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByTestId("lab-outcome-EXECUTED")).toBeInTheDocument());
    expect(screen.queryByTestId("lab-benchmark-trend-graph")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-benchmark-trend-empty")).not.toBeInTheDocument();
  });

  it("renders benchmark run summary without internal documentation copy", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      id: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      benchmarkKind: "LLM_JUDGE_QA",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: {
        outcomeCounts: { EXECUTED: 36 },
        onExecuted: { n: 36, meanNormalizedExactMatch: 0.5 },
      },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });

    renderPanel({ evaluationRunId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890" });

    await waitFor(() => expect(screen.getByTestId("lab-benchmark-results-panel")).toBeInTheDocument());
    expect(screen.queryByTestId("lab-benchmark-m9-export-path")).not.toBeInTheDocument();
    expect(screen.queryByText(/\bM9\b/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/partial evidence/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\.cursor/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Do not claim/i)).not.toBeInTheDocument();
  });

  it("shows trend empty state for RAG preset runs without scored rows", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { SKIPPED: 1 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({
      items: [
        {
          id: "item-1",
          questionText: "skipped?",
          mvp: {
            operational: { outcome: "SKIPPED", presetCode: "P1", modelId: "m1" },
            generation: {},
          },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByTestId("lab-benchmark-trend-empty")).toBeInTheDocument());
    expect(screen.queryByTestId("lab-benchmark-trend-graph")).not.toBeInTheDocument();
  });

  it("shows three comparison rows for a three-model LLM campaign", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue(baseRun);
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 3 }, onExecuted: { n: 3, meanNormalizedExactMatch: 0.5 } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([
      { runId: "r1-0000-0000-0000", llmModelId: "model-a", status: "SUCCEEDED" },
      { runId: "r2-0000-0000-0000", llmModelId: "model-b", status: "SUCCEEDED" },
      { runId: "r3-0000-0000-0000", llmModelId: "model-c", status: "SUCCEEDED" },
    ]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "LLM_MODEL",
      comparisonAxisLabel: "LLM model",
      rows: [
        { comparisonLabel: "model-b", totalItems: 1, executed: 1, meanExactMatch: 0.9 },
        { comparisonLabel: "model-a", totalItems: 1, executed: 1, meanExactMatch: 0.7 },
        { comparisonLabel: "model-c", totalItems: 1, executed: 1, meanExactMatch: 0.5 },
      ],
    } as never);

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "c1" });

    await waitFor(() => expect(screen.getByTestId("lab-campaign-comparison-panel")).toBeInTheDocument());
    expect(screen.getAllByTestId(/lab-comparison-row-/)).toHaveLength(3);
    expect(screen.getAllByText(/model-b/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/model-c/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId("lab-export-campaign-summary-csv")).toBeInTheDocument();
  });

  it("shows embedding campaign failures without raw unknown labels", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "EMBEDDING_RETRIEVAL",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { FAILED: 1 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({
      items: [
        {
          id: "emb-1",
          questionText: "retrieve this",
          mvp: {
            operational: { outcome: "FAILED", modelId: "_UNKNOWN", presetCode: "_UNKNOWN" },
            generation: { recallAt1: "NOT_AVAILABLE" },
          },
        },
      ],
    });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([
      { runId: "r1", embeddingModelId: "qwen3-embedding:latest", status: "SUCCEEDED" },
    ]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "EMBEDDING_MODEL",
      comparisonAxisLabel: "Embedding model",
      rows: [{ comparisonLabel: "qwen3-embedding:latest", totalItems: 1, failed: 1, executed: 0 }],
    } as never);

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "emb-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-failed-skipped-section")).toBeInTheDocument());
    expect(screen.queryByText(/_UNKNOWN/i)).not.toBeInTheDocument();
    expect(screen.getByText(/qwen3-embedding/i)).toBeInTheDocument();
  });

  it("shows RAG preset labels in the item table", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 1 }, onExecuted: { n: 1, meanNormalizedExactMatch: 0.8 } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({
      items: [
        {
          id: "rag-1",
          questionText: "What is RAG?",
          metricsPayload: { presetLabel: "Baseline hybrid RAG" },
          mvp: {
            operational: { outcome: "EXECUTED", presetCode: "P2", modelId: "llama3" },
            generation: { correctness: 0.85, llmJudgeScore: 0.8 },
          },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id });

    await waitFor(() => expect(screen.getByTestId("lab-benchmark-results-panel")).toBeInTheDocument());
    expect(screen.getAllByText("P2").length).toBeGreaterThanOrEqual(1);
  });

  it("renders preset labels for RAG preset campaign comparison rows", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 0 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([
      { runId: "r-p0", presetCode: "P0", presetLabel: "Corpus text only", status: "SUCCEEDED" },
      { runId: "r-p2", presetCode: "P2", presetLabel: "Document-level dense retrieval", status: "SUCCEEDED" },
    ]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "PRESET_CODE",
      comparisonAxisLabel: "RAG preset",
      rows: [
        {
          presetKey: "P2",
          presetLabel: "Document-level dense retrieval",
          modelLabel: "gemma3:4b",
          totalItems: 60,
          executed: 60,
          skipped: 0,
          meanExactMatch: 0.8,
        },
        {
          presetKey: "P0",
          presetLabel: "Corpus text only",
          modelLabel: "gemma3:4b",
          totalItems: 60,
          executed: 0,
          skipped: 60,
          meanExactMatch: null,
        },
      ],
    } as never);
    vi.mocked(fetchCampaignItemsBundle).mockResolvedValue({
      items: [
        {
          itemId: "exec-1",
          question: "What is RAG?",
          answer: "Retrieval augmented generation",
          presetCode: "P2",
          presetLabel: "Document-level dense retrieval",
          status: "EXECUTED",
          mvp: {
            generation: { correctness: 0.9 },
            operational: { outcome: "EXECUTED", presetCode: "P2", modelId: "gemma3:4b" },
          },
        },
        {
          itemId: "skip-1",
          question: "Skipped question",
          presetCode: "P0",
          presetLabel: "Corpus text only",
          status: "SKIPPED",
          failureReason: "INDEX_PREPARATION_REQUIRED",
          mvp: {
            operational: {
              outcome: "SKIPPED",
              presetCode: "P0",
              skipReasonCode: "INDEX_PREPARATION_REQUIRED",
            },
          },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "rag-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-campaign-comparison-panel")).toBeInTheDocument());
    expect(screen.getAllByText(/Document-level dense retrieval/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/Corpus text only/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId("lab-campaign-comparison-panel").textContent).not.toMatch(/gemma3:4b/i);
    expect(screen.queryByTestId("lab-benchmark-no-executed-warning")).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-campaign-partial-summary")).toBeInTheDocument();
    expect(screen.getByTestId("lab-outcome-EXECUTED")).toHaveTextContent(/60/i);
    expect(screen.getByText(/What is RAG\?/i)).toBeInTheDocument();
  });

  it("selecting an executed comparison row shows executed items for that preset", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 0 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "PRESET_CODE",
      rows: [
        { presetKey: "P2", presetLabel: "Dense retrieval", executed: 1, skipped: 0, totalItems: 1 },
        { presetKey: "P0", presetLabel: "Corpus text", executed: 0, skipped: 1, totalItems: 1 },
      ],
    } as never);
    vi.mocked(fetchCampaignItemsBundle).mockResolvedValue({
      items: [
        {
          itemId: "p2-item",
          question: "Executed for P2",
          presetCode: "P2",
          presetLabel: "Dense retrieval",
          status: "EXECUTED",
          mvp: { generation: { correctness: 0.5 }, operational: { outcome: "EXECUTED", presetCode: "P2" } },
        },
        {
          itemId: "p0-item",
          question: "Skipped for P0",
          presetCode: "P0",
          presetLabel: "Corpus text",
          status: "SKIPPED",
          failureReason: "INDEX_PREPARATION_REQUIRED",
          mvp: { operational: { outcome: "SKIPPED", presetCode: "P0", skipReasonCode: "INDEX_PREPARATION_REQUIRED" } },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "rag-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-comparison-row-0")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("lab-comparison-row-0"));
    await waitFor(() => expect(screen.getByText(/Executed for P2/i)).toBeInTheDocument());
    expect(screen.queryByText(/Skipped for P0/i)).not.toBeInTheDocument();
  });

  it("selecting a skipped comparison row shows concise skip reasons and collapsed technical details", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { SKIPPED: 1 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "PRESET_CODE",
      rows: [
        { presetKey: "P2", presetLabel: "Dense retrieval", executed: 1, skipped: 0, totalItems: 1 },
        { presetKey: "P0", presetLabel: "Corpus text", executed: 0, skipped: 1, totalItems: 1 },
      ],
    } as never);
    vi.mocked(fetchCampaignItemsBundle).mockResolvedValue({
      items: [
        {
          itemId: "p0-item",
          question: "Skipped for P0",
          presetCode: "P0",
          status: "SKIPPED",
          failureReason: "INDEX_PREPARATION_REQUIRED",
          mvp: { operational: { outcome: "SKIPPED", presetCode: "P0", skipReasonCode: "INDEX_PREPARATION_REQUIRED" } },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "rag-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-comparison-row-1")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("lab-comparison-row-1"));
    await waitFor(() => expect(screen.getByText(/Skipped for P0/i)).toBeInTheDocument());
    expect(screen.getByText(/Index preparation is required/i)).toBeInTheDocument();
    const technical = screen.getByTestId("lab-item-technical-p0-item");
    expect(technical).not.toHaveAttribute("open");
    expect(technical.querySelector("pre")?.textContent).toContain("INDEX_PREPARATION_REQUIRED");
  });

  it("preset filter uses preset identity from comparison rows", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
      ...baseRun,
      benchmarkKind: "RAG_PRESET_END_TO_END",
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 1 }, onExecuted: { n: 1, meanNormalizedExactMatch: 0.5 } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "PRESET_CODE",
      rows: [
        { presetKey: "P2", presetLabel: "Dense retrieval", executed: 1, totalItems: 1 },
        { presetKey: "P0", presetLabel: "Corpus text", executed: 0, skipped: 1, totalItems: 1 },
      ],
    } as never);
    vi.mocked(fetchCampaignItemsBundle).mockResolvedValue({
      items: [
        {
          itemId: "p2-item",
          question: "Only P2",
          presetCode: "P2",
          status: "EXECUTED",
          mvp: { generation: { correctness: 0.5 }, operational: { outcome: "EXECUTED", presetCode: "P2", modelId: "gemma3:4b" } },
        },
        {
          itemId: "p0-item",
          question: "Only P0",
          presetCode: "P0",
          status: "SKIPPED",
          mvp: { operational: { outcome: "SKIPPED", presetCode: "P0" } },
        },
      ],
    });

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "rag-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-results-filter-preset")).toBeInTheDocument());
    const presetSelect = screen.getByTestId("lab-results-filter-preset") as HTMLSelectElement;
    const optionValues = Array.from(presetSelect.options).map((option) => option.value);
    expect(optionValues).toContain("P2");
    expect(optionValues).toContain("P0");
    expect(optionValues).not.toContain("gemma3:4b");

    fireEvent.change(presetSelect, { target: { value: "P2" } });
    await waitFor(() => expect(screen.getByText(/Only P2/i)).toBeInTheDocument());
    expect(screen.queryByText(/Only P0/i)).not.toBeInTheDocument();
  });

  it("shows empty comparison state when campaign has fewer than two rows", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue(baseRun);
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 1 }, onExecuted: { n: 1, meanNormalizedExactMatch: 0.8 } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([
      { runId: "r1", llmModelId: "m1", status: "SUCCEEDED" },
    ]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      campaignType: "LLM",
      comparisonAxis: "LLM_MODEL",
      comparativeMode: false,
      rows: [{ axisValue: "m1", modelLabel: "m1", totalItems: 1, executed: 1 }],
    } as never);

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "single-model-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-campaign-comparison-empty")).toBeInTheDocument());
    expect(screen.queryByTestId("lab-campaign-comparison-panel")).not.toBeInTheDocument();
  });
});
