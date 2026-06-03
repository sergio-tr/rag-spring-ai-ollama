import { describe, it, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabJobPanel } from "./lab-job-panel";

const accepted = { jobId: "j1", status: "x", pollPath: "/p", streamPath: "/s" };

const runningTask = {
  id: "j1",
  taskType: "t",
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

describe("LabJobPanel", () => {
  it("returns null when no job", () => {
    const { container } = render(
      <IntlTestProvider>
        <LabJobPanel accepted={null} taskStatus={null} />
      </IntlTestProvider>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("shows running state chip and copies job id", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={runningTask} />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-job-panel")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/Running/i);
    await user.click(screen.getByRole("button", { name: /copy/i }));
    expect(writeText).toHaveBeenCalledWith("j1");
  });

  it("shows stopped-waiting chip when user aborted local wait", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={runningTask} stoppedWaiting />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(/Reconnecting/i);
  });

  it("exposes data-lab-job-ui-phase for e2e watch assertions", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={runningTask} connectionState="live" />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-job-panel")).toHaveAttribute("data-lab-job-ui-phase", "running");
  });

  it("shows queued chip before first poll tick", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={null} queuedHint />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(/Queued/i);
  });

  it("shows no-items-executed label when closure has zero executed", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel
          accepted={accepted}
          taskStatus={{
            ...runningTask,
            status: "FAILED",
            terminal: true,
            failureCode: "BENCHMARK_ALL_ITEMS_SKIPPED",
            errorMessage: "Every benchmark item was skipped",
            result: {
              benchmarkClosure: {
                expectedItems: 4,
                executedItems: 0,
                skippedItems: 4,
                classification: "COMPLETED_WITH_NO_EXECUTED_ITEMS",
              },
            },
          }}
        />
      </IntlTestProvider>,
    );
    const panel = screen.getByTestId("lab-job-panel");
    expect(within(panel).getAllByRole("status")[0]).toHaveTextContent(/No items executed/i);
    expect(within(panel).getByTestId("lab-benchmark-closure-summary")).toHaveTextContent(/0 executed/i);
    expect(screen.getByTestId("lab-empty-success-warning")).toBeInTheDocument();
  });

  it("shows completed chip for terminal success", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel
          accepted={accepted}
          taskStatus={{
            ...runningTask,
            status: "SUCCEEDED",
            terminal: true,
          }}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(/Completed/i);
  });

  it("shows failed chip when task ends in FAILED terminal state", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel
          accepted={accepted}
          taskStatus={{
            ...runningTask,
            status: "FAILED",
            terminal: true,
            errorMessage: "Classifier rejected payload",
          }}
        />
      </IntlTestProvider>,
    );
    const panel = screen.getByTestId("lab-job-panel");
    expect(within(panel).getAllByRole("status")[0]).toHaveTextContent(/Failed/i);
    expect(within(panel).getByText(/Classifier rejected payload/i)).toBeInTheDocument();
  });

  it("shows watch elapsed line when prop provided during queued phase", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={null} queuedHint watchElapsedSeconds={42} />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-job-elapsed")).toHaveTextContent(/Watching/i);
    expect(screen.getByTestId("lab-job-elapsed")).toHaveTextContent(/42/);
  });

  it("shows technical details inside disclosure without endpoint paths", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={runningTask} />
      </IntlTestProvider>,
    );
    await user.click(screen.getByText(/Technical details/i));
    expect(screen.getByText("j1")).toBeInTheDocument();
    expect(screen.queryByText("/p")).not.toBeInTheDocument();
    expect(screen.queryByText("/s")).not.toBeInTheDocument();
  });

  it("renders progress summary and subtasks from structured events", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel
          accepted={accepted}
          taskStatus={runningTask}
          recentEvents={[
            {
              eventId: 1,
              jobId: "j1",
              type: "DATASET_RESOLVED",
              status: "RUNNING",
              progress: null,
              message: "Dataset ready · 2 questions",
              timestamp: "2026-01-01T00:00:00Z",
              payload: { phase: "DATASET" },
            },
            {
              eventId: 2,
              jobId: "j1",
              type: "ITEM_COMPLETED",
              status: "RUNNING",
              progress: null,
              message: "P0 · Question 1/2",
              timestamp: "2026-01-01T00:00:01Z",
              runCompletedItems: 1,
              runTotalItems: 2,
              currentPresetCode: "P0",
              payload: { phase: "RAG_EVALUATION" },
            },
          ]}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-progress-summary")).toBeInTheDocument();
    expect(screen.getByTestId("lab-subtask-list")).toBeInTheDocument();
    expect(screen.getByTestId("lab-job-item-counter")).toHaveTextContent(/1\s*\/\s*2/);
    expect(screen.queryByText(/Resolving typed dataset/i)).not.toBeInTheDocument();
  });

  it("shows global item counter when campaign totals are present", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel
          accepted={accepted}
          taskStatus={runningTask}
          recentEvents={[
            {
              eventId: 1,
              jobId: "j1",
              type: "ITEM_COMPLETED",
              status: "RUNNING",
              progress: null,
              payload: null,
              message: "Progress",
              timestamp: "2026-01-01T00:00:00Z",
              globalCompletedItems: 5,
              globalTotalItems: 108,
              runCompletedItems: 5,
              runTotalItems: 36,
            },
          ]}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-job-item-counter")).toHaveTextContent(/5\s*\/\s*108/);
  });
});
