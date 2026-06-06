import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabFailedJobResultsNotice } from "./lab-failed-job-results-notice";

describe("LabFailedJobResultsNotice", () => {
  it("shows failed notice with runId and error message", () => {
    render(
      <IntlTestProvider>
        <LabFailedJobResultsNotice
          evaluationRunId="fc9ea380-b255-43e2-b203-3b900804ffc9"
          taskStatus={{
            terminal: true,
            status: "FAILED",
            errorMessage: "NullPointerException",
          }}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-failed-job-results-notice")).toBeInTheDocument();
    expect(screen.getByTestId("lab-failed-job-status-badge")).toHaveTextContent(/failed/i);
    expect(screen.getByTestId("lab-failed-job-run-id")).toHaveTextContent("fc9ea380");
    expect(screen.getByTestId("lab-failed-job-error-message")).toBeInTheDocument();
  });
});
