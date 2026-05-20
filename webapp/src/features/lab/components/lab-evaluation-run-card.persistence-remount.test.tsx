import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor, within } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { AsyncTaskStatusDto } from "@/types/api";

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

describe("LabEvaluationRunCard persistence across remount", () => {
  beforeEach(() => {
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0 });
  });

  it("rehydrates LabJobPanel from persisted session records after full unmount", async () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: {
        jobId: "persist-rm",
        status: "QUEUED",
        pollPath: "/lab/jobs/persist-rm",
        streamPath: "/lab/jobs/persist-rm/events",
      },
      sectionKey: "evaluation-llm",
      followMode: "poll",
      taskTypeHint: "LLM_EVALUATION",
    });
    const tick: AsyncTaskStatusDto = {
      id: "persist-rm",
      taskType: "LAB",
      status: "RUNNING",
      progressText: null,
      result: null,
      errorMessage: null,
      terminal: false,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
    };
    useLabJobSessionStore.getState().patchLabJobFromTick("persist-rm", tick);

    const first = render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard
            benchmarkKind="LLM_JUDGE_QA"
            sectionKey="evaluation-llm"
            taskTypeHint="LLM_EVALUATION"
            cardTitle="LLM evaluation"
            cardDescription="desc"
            runButtonTestId="lab-llm-run"
            radioGroupName="follow-remount"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const panel1 = await waitFor(() => first.getByTestId("lab-job-panel"));
    expect(within(panel1).getByRole("status")).toHaveTextContent(/Running/i);

    first.unmount();

    const second = render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard
            benchmarkKind="LLM_JUDGE_QA"
            sectionKey="evaluation-llm"
            taskTypeHint="LLM_EVALUATION"
            cardTitle="LLM evaluation"
            cardDescription="desc"
            runButtonTestId="lab-llm-run"
            radioGroupName="follow-remount-2"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const panel2 = await waitFor(() => second.getByTestId("lab-job-panel"));
    expect(within(panel2).getByRole("status")).toHaveTextContent(/Running/i);
  });
});
