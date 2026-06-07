import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import LabEmbeddingEvalPage from "./page";

vi.mock("@/features/lab/components/lab-evaluation-run-card", () => ({
  LabEvaluationRunCard: () => <div data-testid="lab-eval-run-card" />,
}));

describe("LabEmbeddingEvalPage", () => {
  it("renders compact summary and collapsed help", () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEmbeddingEvalPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId("lab-embedding-eval-page")).toBeInTheDocument();
    expect(screen.getByTestId("lab-eval-guided-help")).toBeInTheDocument();
    expect(screen.queryByText(/Guided steps/i)).not.toBeInTheDocument();
  });
});
