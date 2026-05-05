import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { LabBenchmarkResultsPanel } from "./lab-benchmark-results-panel";

vi.mock("@/features/lab/lib/lab-benchmark-results-api", () => ({
  fetchLabEvaluationRun: vi.fn(),
  fetchLabCampaignRuns: vi.fn(),
  fetchMvpRollupsJson: vi.fn(),
  fetchMvpItemsBundle: vi.fn(),
  downloadMvpExport: vi.fn(),
  downloadCampaignMvpItemsJson: vi.fn(),
}));

import {
  fetchLabEvaluationRun,
  fetchLabCampaignRuns,
  fetchMvpItemsBundle,
  fetchMvpRollupsJson,
} from "@/features/lab/lib/lab-benchmark-results-api";
import { ApiError } from "@/lib/api-client";

describe("LabBenchmarkResultsPanel", () => {
  beforeEach(() => {
    vi.mocked(fetchLabEvaluationRun).mockReset();
    vi.mocked(fetchLabCampaignRuns).mockReset();
    vi.mocked(fetchMvpRollupsJson).mockReset();
    vi.mocked(fetchMvpItemsBundle).mockReset();
  });

  it("loads rollups and shows outcome badges when enabled", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
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
          mvp: { operational: { outcome: "NOT_SUPPORTED", unsupportedReason: "NOPE" } },
        },
      ],
    });

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabBenchmarkResultsPanel evaluationRunId="550e8400-e29b-41d4-a716-446655440099" loadEnabled />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("lab-outcome-NOT_SUPPORTED")).toBeInTheDocument());
    expect(screen.getByTestId("lab-outcome-EXECUTED")).toHaveTextContent(/1/i);
  });

  it("shows friendly error when MVP bundle fetch fails", async () => {
    vi.mocked(fetchLabEvaluationRun).mockRejectedValueOnce(new ApiError(403, "denied", { kind: "http" }));

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabBenchmarkResultsPanel evaluationRunId="550e8400-e29b-41d4-a716-446655440099" loadEnabled />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("lab-benchmark-results-error")).toBeInTheDocument());
    expect(screen.getByRole("alert")).toHaveTextContent(/denied/i);
  });

  it("renders unknown outcome keys from rollup counts", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
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
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: {
        outcomeCounts: { EXECUTED: 1, CUSTOM_LABEL: 2 },
        onExecuted: { n: 0, meanNormalizedExactMatch: null },
      },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabBenchmarkResultsPanel evaluationRunId="550e8400-e29b-41d4-a716-446655440099" loadEnabled />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByText(/CUSTOM_LABEL: 2/i)).toBeInTheDocument());
  });

  it("shows campaign export and run list when campaignId is provided", async () => {
    vi.mocked(fetchLabEvaluationRun).mockResolvedValue({
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
    });
    vi.mocked(fetchMvpRollupsJson).mockResolvedValue({
      globalMacro: { outcomeCounts: { EXECUTED: 1 }, onExecuted: { n: 0, meanNormalizedExactMatch: null } },
    });
    vi.mocked(fetchMvpItemsBundle).mockResolvedValue({ items: [] });
    vi.mocked(fetchLabCampaignRuns).mockResolvedValue([
      { runId: "r1-0000-0000-0000", llmModelId: "m1", status: "SUCCEEDED" },
      { runId: "r2-0000-0000-0000", llmModelId: "m2", status: "SUCCEEDED" },
    ]);

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabBenchmarkResultsPanel
            evaluationRunId="550e8400-e29b-41d4-a716-446655440099"
            campaignId="c1"
            loadEnabled
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("lab-export-campaign-items-json")).toBeInTheDocument());
    expect(screen.getByTestId("lab-campaign-runs-panel")).toBeInTheDocument();
    expect(screen.getByText(/m1/i)).toBeInTheDocument();
  });
});
