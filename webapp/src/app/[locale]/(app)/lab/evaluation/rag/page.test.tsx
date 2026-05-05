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
  it("renders guided steps for P0-P14 and avoids legacy endpoint copy", () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabRagEvalPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/Guided steps/i)).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-preset-explainer")).toBeInTheDocument();
    expect(screen.getByText(/P0–P8/i)).toBeInTheDocument();
    expect(screen.queryByText(/\/lab\/evaluations/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-eval-run-card")).toBeInTheDocument();
  });
});

