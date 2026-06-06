import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor, within } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ActiveLabJobDto, LatestLabRunRecoveryDto } from "@/types/api";

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

const liveStreamMock = vi.fn(() => ({
  connectionState: "idle" as const,
  taskStatus: null,
  lastEventId: null,
  recentEvents: [],
  progressSnapshot: undefined,
  resume: vi.fn(),
  stop: vi.fn(),
}));

vi.mock("@/features/lab/hooks/use-lab-job-live-stream", () => ({
  useLabJobLiveStream: (...args: unknown[]) => liveStreamMock(...args),
}));

const fetchLabJobStatusOnceMock = vi.fn();

vi.mock("@/lib/async-task", () => ({
  fetchLabJobStatusOnce: (...args: unknown[]) => fetchLabJobStatusOnceMock(...args),
  pollLabJob: vi.fn(),
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
    hasResults: true,
  };
}

function activeBackendJob(): ActiveLabJobDto {
  return {
    jobId: "active-job",
    evaluationRunId: "550e8400-e29b-41d4-a716-446655440099",
    benchmarkKind: "LLM_JUDGE_QA",
    projectId: null,
    datasetId: null,
    status: "RUNNING",
    progress: null,
    startedAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:01:00Z",
    pollPath: "/lab/jobs/active-job",
    streamPath: "/lab/jobs/active-job/events",
    cancellable: true,
  };
}

function cardProps(radioGroupName: string) {
  return {
    benchmarkKind: "LLM_JUDGE_QA" as const,
    sectionKey: "evaluation-llm" as const,
    taskTypeHint: "LLM_EVALUATION",
    cardTitle: "LLM evaluation",
    runButtonTestId: "lab-llm-run",
    radioGroupName,
  };
}

describe("LabEvaluationRunCard latest run recovery", () => {
  beforeEach(() => {
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0, forgetWatchNonce: 0 });
    liveStreamMock.mockClear();
    fetchLabJobStatusOnceMock.mockReset();
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
          <LabEvaluationRunCard {...cardProps("latest-run")} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(document.querySelector("[data-testid='lab-job-panel']")).toBeTruthy();
    });
    const panel = document.querySelector("[data-testid='lab-job-panel']")!;
    expect(within(panel as HTMLElement).getByRole("status")).toHaveTextContent(/Completed|finished/i);
  });

  it("resumes from backend active job when sessionStorage is empty", async () => {
    vi.mocked(useActiveLabJobs).mockReturnValue({
      data: [activeBackendJob()],
      isFetched: true,
      isLoading: false,
      isError: false,
      error: null,
    } as never);
    fetchLabJobStatusOnceMock.mockResolvedValue({
      id: "active-job",
      taskType: "LLM_EVALUATION",
      status: "RUNNING",
      terminal: false,
      progressText: null,
      errorMessage: null,
      failureCode: null,
      result: null,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
    });
    latestRunMock.mockReturnValue({
      data: undefined,
      isFetched: false,
      isLoading: false,
      isError: false,
    });

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard {...cardProps("active-job")} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(fetchLabJobStatusOnceMock).toHaveBeenCalledWith("active-job");
    });
    await waitFor(() => {
      expect(liveStreamMock).toHaveBeenCalled();
    });
    const lastCall = liveStreamMock.mock.calls.at(-1)?.[0] as { enabled?: boolean; jobId?: string };
    expect(lastCall?.jobId).toBe("active-job");
    expect(lastCall?.enabled).toBe(true);
  });
});

describe("LabEvaluationRunCard stop watching semantics", () => {
  beforeEach(() => {
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0, forgetWatchNonce: 0 });
    liveStreamMock.mockClear();
    fetchLabJobStatusOnceMock.mockReset();
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

  it("stop watching clears session tracking and reloads latest run from backend", async () => {
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
          <LabEvaluationRunCard {...cardProps("stop-watching")} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(document.querySelector("[data-testid='lab-job-panel']")).toBeTruthy();
    });

    useLabJobSessionStore.getState().forgetLabJobWatching("latest-job");
    expect(useLabJobSessionStore.getState().records).toHaveLength(0);
    expect(useLabJobSessionStore.getState().forgetWatchNonce).toBe(1);

    await waitFor(() => {
      expect(document.querySelector("[data-testid='lab-job-panel']")).toBeTruthy();
    });
  });

  it("ignores stale non-terminal session when backend poll returns terminal", async () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: {
        jobId: "stale-job",
        status: "RUNNING",
        pollPath: "/lab/jobs/stale-job",
        streamPath: "/lab/jobs/stale-job/events",
      },
      sectionKey: "evaluation-llm",
      followMode: "sse",
      taskTypeHint: "LLM_EVALUATION",
      evaluationRunId: "550e8400-e29b-41d4-a716-446655440099",
    });
    fetchLabJobStatusOnceMock.mockResolvedValue({
      id: "stale-job",
      taskType: "LLM_EVALUATION",
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
          <LabEvaluationRunCard {...cardProps("stale-session")} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(fetchLabJobStatusOnceMock).toHaveBeenCalledWith("stale-job");
    });
    await waitFor(() => {
      const panel = document.querySelector("[data-testid='lab-job-panel']");
      expect(panel).toBeTruthy();
      expect(within(panel as HTMLElement).getByRole("status")).toHaveTextContent(/Completed|finished/i);
    });
    const lastStreamOpts = liveStreamMock.mock.calls.at(-1)?.[0] as { enabled?: boolean } | undefined;
    expect(lastStreamOpts?.enabled).not.toBe(true);
  });
});
