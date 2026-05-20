import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import { initialSnapshotFromAccepted, type PersistedLabJobRecord } from "@/features/lab/lib/lab-job-persistence";
import { IntlTestProvider } from "@/test-utils/intl";
import type { LabJobAcceptedDto } from "@/types/api";

const { followLabJob } = vi.hoisted(() => ({
  followLabJob: vi.fn(),
}));

vi.mock("@/lib/lab-job-follow", () => ({
  followLabJob: (...args: unknown[]) => followLabJob(...args),
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

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: null }) => unknown) => selector({ activeProject: null }),
}));

import { LabEvaluationRunCard } from "./lab-evaluation-run-card";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";

function baseAccepted(jobId: string): LabJobAcceptedDto {
  return {
    jobId,
    status: "QUEUED",
    pollPath: `/lab/jobs/${jobId}`,
    streamPath: `/lab/jobs/${jobId}/events`,
  };
}

describe("LabEvaluationRunCard resume follow", () => {
  beforeEach(() => {
    followLabJob.mockReset();
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0 });
    followLabJob.mockResolvedValue({
      id: "resume-job",
      taskType: "LAB",
      status: "SUCCEEDED",
      progressText: null,
      result: {},
      errorMessage: null,
      terminal: true,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
    });
  });

  it("consumes pending resume and calls followLabJob with persisted acceptance", async () => {
    const acceptedDto = baseAccepted("resume-job");
    const persisted: PersistedLabJobRecord = {
      jobId: acceptedDto.jobId,
      sectionKey: "evaluation-llm",
      accepted: acceptedDto,
      followMode: "poll",
      startedAtMs: Date.now(),
      lastUpdatedMs: Date.now(),
      lastStatus: initialSnapshotFromAccepted(acceptedDto, "LLM_EVALUATION"),
      stoppedWatching: false,
      staleNotFound: false,
      pollTimedOut: false,
      dismissedTerminal: false,
    };
    useLabJobSessionStore.setState({ records: [persisted] });
    useLabJobSessionStore.getState().requestResumeLabJob("evaluation-llm", "resume-job");

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

    await waitFor(() => expect(followLabJob).toHaveBeenCalledTimes(1));
    expect(followLabJob.mock.calls[0][0]).toEqual(acceptedDto);
  });
});
