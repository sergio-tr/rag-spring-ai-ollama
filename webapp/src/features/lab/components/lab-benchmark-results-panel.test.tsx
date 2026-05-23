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
  fetchMvpRollupsJson: vi.fn(),
  fetchMvpItemsBundle: vi.fn(),
  downloadMvpExport: vi.fn(),
  downloadCampaignMvpItemsJson: vi.fn(),
  downloadCampaignExport: vi.fn(),
}));

import {
  fetchLabEvaluationRun,
  fetchLabCampaignRuns,
  fetchCampaignComparison,
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

    await waitFor(() => expect(screen.getByText(/CUSTOM_LABEL: 2/i)).toBeInTheDocument());
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
      { runId: "r1", embeddingModelId: "bge-m3", status: "SUCCEEDED" },
    ]);
    vi.mocked(fetchCampaignComparison).mockResolvedValue({
      comparisonAxis: "EMBEDDING_MODEL",
      comparisonAxisLabel: "Embedding model",
      rows: [{ comparisonLabel: "bge-m3", totalItems: 1, failed: 1, executed: 0 }],
    } as never);

    renderPanel({ evaluationRunId: baseRun.id, campaignId: "emb-campaign" });

    await waitFor(() => expect(screen.getByTestId("lab-failed-skipped-section")).toBeInTheDocument());
    expect(screen.queryByText(/_UNKNOWN/i)).not.toBeInTheDocument();
    expect(screen.getByText(/bge-m3/i)).toBeInTheDocument();
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
