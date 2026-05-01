import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor, within } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import type { AsyncTaskStatusDto } from "@/types/api";

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(() => ({
    data: {
      datasets: { enabled: true, questionCount: 12 },
      evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
      classifier: { configured: true, train: true, evaluate: true },
      message: "",
    },
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
      <IntlTestProvider>
        <LabEvaluationRunCard
          evalBasePath="/lab/evaluations/llm"
          cardTitle="LLM evaluation"
          cardDescription="desc"
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-remount"
        />
      </IntlTestProvider>,
    );
    const panel1 = await waitFor(() => first.getByTestId("lab-job-panel"));
    expect(within(panel1).getByRole("status")).toHaveTextContent(/Running/i);

    first.unmount();

    const second = render(
      <IntlTestProvider>
        <LabEvaluationRunCard
          evalBasePath="/lab/evaluations/llm"
          cardTitle="LLM evaluation"
          cardDescription="desc"
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-remount-2"
        />
      </IntlTestProvider>,
    );
    const panel2 = await waitFor(() => second.getByTestId("lab-job-panel"));
    expect(within(panel2).getByRole("status")).toHaveTextContent(/Running/i);
  });
});
