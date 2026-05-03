import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabEvaluationRunCard } from "./lab-evaluation-run-card";

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(),
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    selector({ activeProject: null }),
}));

import { useLabStatus } from "@/features/lab/hooks/use-lab-status";

describe("LabEvaluationRunCard", () => {
  beforeEach(() => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasets: { enabled: true, questionCount: 12 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
    } as never);
  });

  it("shows user-facing description without HTTP 202 jargon on the card surface", () => {
    render(
      <IntlTestProvider>
        <LabEvaluationRunCard
          evalBasePath="/lab/evaluations/llm"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/Benchmark the configured LLM/i)).toBeInTheDocument();
    expect(screen.queryByText(/HTTP 202/i)).not.toBeInTheDocument();
  });

  it("shows honest dataset-disabled warning while keeping primary copy readable", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasets: { enabled: false, questionCount: 0 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
    } as never);
    render(
      <IntlTestProvider>
        <LabEvaluationRunCard
          evalBasePath="/lab/evaluations/llm"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/Benchmark questions are unavailable/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run evaluation/i })).toBeDisabled();
  });

  it("keeps technical sync hint inside advanced disclosure by default", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <LabEvaluationRunCard
          evalBasePath="/lab/evaluations/llm"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </IntlTestProvider>,
    );
    const advancedDetails = screen.getByText(/Advanced options/i).closest("details");
    expect(advancedDetails).not.toHaveAttribute("open");
    expect(advancedDetails).toHaveTextContent(/sync=true/);
    await user.click(screen.getByText(/Advanced options/i));
    expect(advancedDetails).toHaveAttribute("open");
  });
});
