import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import type { AsyncTaskStatusDto } from "@/types/api";
import { LabFailedJobResultsNotice } from "./lab-failed-job-results-notice";

const failedTaskStatus: AsyncTaskStatusDto = {
  id: "task-1",
  taskType: "RAG_EVALUATION",
  status: "FAILED",
  progressText: null,
  result: null,
  errorMessage: "NullPointerException",
  terminal: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  startedAt: null,
  completedAt: "2026-01-01T00:00:01Z",
};

describe("LabFailedJobResultsNotice", () => {
  it("shows failed notice with runId and error message", () => {
    render(
      <IntlTestProvider>
        <LabFailedJobResultsNotice
          evaluationRunId="fc9ea380-b255-43e2-b203-3b900804ffc9"
          taskStatus={failedTaskStatus}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-failed-job-results-notice")).toBeInTheDocument();
    expect(screen.getByTestId("lab-failed-job-status-badge")).toHaveTextContent(/failed/i);
    expect(screen.getByTestId("lab-failed-job-run-id")).toHaveTextContent("fc9ea380");
    expect(screen.getByTestId("lab-failed-job-error-message")).toBeInTheDocument();
  });

  it("omits error notice when task status has no message", () => {
    render(
      <IntlTestProvider>
        <LabFailedJobResultsNotice evaluationRunId="run-12345678" taskStatus={null} />
      </IntlTestProvider>,
    );
    expect(screen.queryByTestId("lab-failed-job-error-message")).not.toBeInTheDocument();
  });
});
