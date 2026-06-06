import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor, within } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { LatestLabRunRecoveryDto } from "@/types/api";

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(() => ({
    data: {
      datasetKindsReady: true,
      datasets: { enabled: true, datasetKindsReady: true },
      evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
      classifier: { configured: true, train: true, evaluate: true },
      message: "",
    },
  })),
}));

vi.mock("@/features/chat/hooks/use-models-by-type", () => ({
  useModelsByType: vi.fn(() => ({ data: [], isLoading: false, isFetched: true })),
}));

vi.mock("@/features/lab/hooks/use-evaluation-corpus", () => ({
  useEvaluationCorpus: vi.fn(() => ({
    summary: { documentCount: 2, readyCount: 2, documents: [] },
    loading: false,
    error: null,
    refresh: vi.fn(),
    ensureCorpus: vi.fn(),
    uploadDocuments: vi.fn(),
    corpusReady: true,
    corpusProcessing: false,
    attachFromProject: vi.fn(),
  })),
}));

vi.mock("@/features/lab/hooks/use-experimental-preset-catalog", () => ({
  useExperimentalPresetCatalog: vi.fn(() => ({ data: [], isLoading: false, isFetched: true })),
}));

vi.mock("@/features/lab/hooks/use-experimental-datasets", () => ({
  useExperimentalDatasetsQuery: vi.fn(() => ({
    data: [
      {
        id: "550e8400-e29b-41d4-a716-446655440000",
        name: "ds",
        experimentalDatasetType: "LLM_MODEL_BASELINE",
        persistedEvaluationDatasetType: "LLM_ONLY",
        readOnly: false,
        questionCount: 2,
        rowCount: 2,
        validationStatus: "VALID",
        uploadedAt: "2026-01-01T00:00:00Z",
        description: null,
      },
    ],
    isLoading: false,
    isFetched: true,
    isSuccess: true,
  })),
}));

const latestRunMock = vi.fn();

vi.mock("@/features/lab/hooks/use-latest-lab-benchmark-run", () => ({
  useLatestLabBenchmarkRun: (...args: unknown[]) => latestRunMock(...args),
}));

vi.mock("@/features/lab/hooks/use-lab-job-live-stream", () => ({
  useLabJobLiveStream: vi.fn(() => ({
    connectionState: "idle" as const,
    taskStatus: null,
    lastEventId: null,
    recentEvents: [],
    progressSnapshot: undefined,
    resume: vi.fn(),
    stop: vi.fn(),
  })),
}));

vi.mock("@/lib/async-task", () => ({
  fetchLabJobStatusOnce: vi.fn(async () => ({
    id: "latest-job",
    taskType: "LAB",
    status: "SUCCEEDED",
    terminal: true,
    progressText: null,
    errorMessage: null,
    failureCode: null,
    result: { recovered: true },
  })),
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: null }) => unknown) => selector({ activeProject: null }),
}));

import { LabEvaluationRunCard } from "./lab-evaluation-run-card";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";

vi.mock("@/features/lab/hooks/use-active-lab-jobs", () => ({
  useActiveLabJobs: vi.fn(),
}));

function completedLatestRun(): LatestLabRunRecoveryDto {
  return {
    evaluationRunId: "550e8400-e29b-41d4-a716-446655440099",
    jobId: "latest-job",
    benchmarkKind: "LLM_JUDGE_QA",
    projectId: null,
    status: "SUCCEEDED",
    terminal: true,
    pollPath: "/lab/jobs/latest-job",
    streamPath: "/lab/jobs/latest-job/events",
    result: { recovered: true },
  };
}

describe("LabEvaluationRunCard latest run recovery", () => {
  beforeEach(() => {
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0 });
    vi.mocked(useActiveLabJobs).mockReturnValue({
      data: [],
      isFetched: true,
      isLoading: false,
      isError: false,
      error: null,
    } as never);
    latestRunMock.mockReturnValue({
      data: completedLatestRun(),
      isFetched: true,
      isLoading: false,
      isError: false,
    });
  });

  it("shows completed job panel from backend latest run with empty sessionStorage", async () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard
            benchmarkKind="LLM_JUDGE_QA"
            sectionKey="evaluation-llm"
            taskTypeHint="LLM_EVALUATION"
            cardTitle="LLM evaluation"
            runButtonTestId="lab-llm-run"
            radioGroupName="latest-run"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(document.querySelector("[data-testid='lab-job-panel']")).toBeTruthy();
    });
    const panel = document.querySelector("[data-testid='lab-job-panel']")!;
    expect(within(panel as HTMLElement).getByRole("status")).toHaveTextContent(/Completed|finished/i);
  });
});

describe("LabEvaluationRunCard forget job semantics", () => {
  beforeEach(() => {
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0 });
    vi.mocked(useActiveLabJobs).mockReturnValue({
      data: [],
      isFetched: true,
      isLoading: false,
      isError: false,
      error: null,
    } as never);
    latestRunMock.mockReturnValue({
      data: completedLatestRun(),
      isFetched: true,
      isLoading: false,
      isError: false,
    });
  });

  it("forget job clears session tracking but latest run remains recoverable", async () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: {
        jobId: "latest-job",
        status: "SUCCEEDED",
        pollPath: "/lab/jobs/latest-job",
        streamPath: "/lab/jobs/latest-job/events",
      },
      sectionKey: "evaluation-llm",
      followMode: "sse",
      taskTypeHint: "LLM_EVALUATION",
      evaluationRunId: "550e8400-e29b-41d4-a716-446655440099",
    });
    useLabJobSessionStore.getState().patchLabJobFromTick("latest-job", {
      id: "latest-job",
      taskType: "LAB",
      status: "SUCCEEDED",
      terminal: true,
      progressText: null,
      errorMessage: null,
      failureCode: null,
      result: { recovered: true },
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: "",
    });

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard
            benchmarkKind="LLM_JUDGE_QA"
            sectionKey="evaluation-llm"
            taskTypeHint="LLM_EVALUATION"
            cardTitle="LLM evaluation"
            runButtonTestId="lab-llm-run"
            radioGroupName="forget-job"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(document.querySelector("[data-testid='lab-job-panel']")).toBeTruthy();
    });
    useLabJobSessionStore.getState().clearLabJobRecord("latest-job");
    expect(useLabJobSessionStore.getState().records).toHaveLength(0);

    await waitFor(() => {
      expect(document.querySelector("[data-testid='lab-job-panel']")).toBeTruthy();
    });
    const panelAfterForget = document.querySelector("[data-testid='lab-job-panel']");
    expect(panelAfterForget).toBeTruthy();
  });
});
