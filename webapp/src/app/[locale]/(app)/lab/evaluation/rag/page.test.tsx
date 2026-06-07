import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import LabRagEvalPage from "./page";

vi.mock("@/features/lab/components/lab-evaluation-run-card", () => ({
  LabEvaluationRunCard: () => <div data-testid="lab-eval-run-card" />,
}));

describe("LabRagEvalPage", () => {
  it("hides preset guide by default and shows compact run section", () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabRagEvalPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId("lab-rag-eval-page")).toBeInTheDocument();
    expect(screen.queryByText(/How to read P0/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-preset-help")).toBeInTheDocument();
    expect(screen.queryByTestId("lab-unsupported-preset-card")).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-eval-run-card")).toBeInTheDocument();
  });
});
