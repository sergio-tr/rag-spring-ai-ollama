import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import type { ActiveLabJobDto } from "@/types/api";

const { useLabJobLiveStreamMock } = vi.hoisted(() => ({
  useLabJobLiveStreamMock: vi.fn(() => ({
    connectionState: "live" as const,
    taskStatus: null,
    lastEventId: null,
    resume: vi.fn(),
    stop: vi.fn(),
  })),
}));

vi.mock("@/features/lab/hooks/use-lab-job-live-stream", () => ({
  useLabJobLiveStream: useLabJobLiveStreamMock,
}));

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

vi.mock("@/features/lab/hooks/use-active-lab-jobs", () => ({
  useActiveLabJobs: vi.fn(),
}));

vi.mock("@/lib/async-task", () => ({
  fetchLabJobStatusOnce: vi.fn(async () => ({
    id: "resume-job",
    taskType: "LAB",
    status: "RUNNING",
    terminal: false,
    progressText: null,
    errorMessage: null,
    failureCode: null,
    result: null,
  })),
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: null }) => unknown) => selector({ activeProject: null }),
}));

import { LabEvaluationRunCard } from "./lab-evaluation-run-card";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";

function activeJob(jobId: string): ActiveLabJobDto {
  return {
    jobId,
    benchmarkKind: "LLM_JUDGE_QA",
    evaluationRunId: "550e8400-e29b-41d4-a716-446655440001",
    projectId: null,
    datasetId: null,
    status: "RUNNING",
    progress: null,
    startedAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:01:00.000Z",
    pollPath: `/lab/jobs/${jobId}`,
    streamPath: `/lab/jobs/${jobId}/events`,
    cancellable: true,
  };
}

describe("LabEvaluationRunCard resume follow", () => {
  beforeEach(() => {
    useLabJobLiveStreamMock.mockReset();
    useLabJobLiveStreamMock.mockReturnValue({
      connectionState: "live",
      taskStatus: null,
      lastEventId: null,
      resume: vi.fn(),
      stop: vi.fn(),
    });
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0 });
    vi.mocked(useActiveLabJobs).mockReturnValue({
      data: [activeJob("resume-job")],
      isFetched: true,
      isLoading: false,
      isError: false,
      error: null,
    } as never);
  });

  it("auto-opens SSE when GET /lab/jobs/active returns a matching job", async () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard
            benchmarkKind="LLM_JUDGE_QA"
            sectionKey="evaluation-llm"
            taskTypeHint="LLM_EVALUATION"
            cardTitle="LLM evaluation"
            cardDescription="desc"
            runButtonTestId="lab-llm-run"
            radioGroupName="follow-resume"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() =>
      expect(useLabJobLiveStreamMock).toHaveBeenCalledWith(
        expect.objectContaining({
          jobId: "resume-job",
          enabled: true,
        }),
      ),
    );
  });
});
