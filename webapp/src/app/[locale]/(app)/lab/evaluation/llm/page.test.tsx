import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import LabLlmEvalPage from "./page";

vi.mock("@/features/lab/components/lab-evaluation-run-card", () => ({
  LabEvaluationRunCard: () => <div data-testid="lab-eval-run-card" />,
}));

describe("LabLlmEvalPage", () => {
  it("renders compact summary and collapsed help without endpoint boilerplate", () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabLlmEvalPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId("lab-llm-eval-page")).toBeInTheDocument();
    expect(screen.getByTestId("lab-llm-eval-prompt-hint")).toHaveTextContent(/Assistant Configuration/i);
    expect(screen.getByTestId("lab-eval-guided-help")).toBeInTheDocument();
    expect(screen.queryByText(/Guided steps/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/lab\/benchmarks/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-eval-run-card")).toBeInTheDocument();
  });
});
