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
    expect(screen.getByRole("status")).toHaveTextContent(/Stopped waiting/i);
  });

  it("shows queued chip before first poll tick", () => {
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={null} queuedHint />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(/Queued/i);
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

  it("shows technical details inside disclosure", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <LabJobPanel accepted={accepted} taskStatus={runningTask} />
      </IntlTestProvider>,
    );
    await user.click(screen.getByText(/Technical details/i));
    expect(screen.getByText("/p")).toBeInTheDocument();
  });
});
