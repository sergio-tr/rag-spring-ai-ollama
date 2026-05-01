import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import LabOverviewPage from "./page";

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(),
}));

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

import { useLabStatus } from "@/features/lab/hooks/use-lab-status";

describe("LabOverviewPage", () => {
  beforeEach(() => {
    vi.mocked(useLabStatus).mockReset();
  });

  it("shows error state without throwing when status fetch fails", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      refetch: vi.fn(),
    } as never);
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/Could not load lab status/i);
  });

  it("shows evaluation dataset as available when backend reports questions", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasets: { enabled: true, questionCount: 12 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    } as never);
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/12 questions loaded/i)).toBeInTheDocument();
    expect(screen.queryByText(/bundled benchmark workbook/i)).not.toBeInTheDocument();
  });

  it("shows simplified overview copy without endpoint-heavy primer text", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasets: { enabled: false, questionCount: 0 },
        evaluations: { llm: true, rag: false, classifierProxy: false, asyncJobs: true },
        classifier: { configured: false, train: false, evaluate: false },
        message: "",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    } as never);
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/See what is ready to run/i)).toBeInTheDocument();
    expect(screen.queryByText(/Feature flags from GET/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\{product\}/i)).not.toBeInTheDocument();
    expect(screen.getByText(/bundled benchmark workbook/i)).toBeInTheDocument();
  });

  it("collapses technical server status lines behind a disclosure by default", async () => {
    const user = userEvent.setup();
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasets: { enabled: true, questionCount: 1 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "Lab API — default async worker GET /lab/status",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    } as never);
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const serverDetails = screen.getByText(/Server note/i).closest("details");
    expect(serverDetails).not.toHaveAttribute("open");
    expect(serverDetails).toHaveTextContent(/Lab API — default async worker/i);
    await user.click(screen.getByText(/Server note/i));
    expect(serverDetails).toHaveAttribute("open");
  });

  it("does not surface compose observability filenames in the primary observability card", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasets: { enabled: true, questionCount: 1 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    } as never);
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/Distributed tracing/i)).toBeInTheDocument();
    expect(screen.queryByText(/compose\.obs\.yml/i)).not.toBeInTheDocument();
  });

  it("shows loading copy when status is loading", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: undefined,
      isError: false,
      isLoading: true,
      refetch: vi.fn(),
    } as never);
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/Loading status/i)).toBeInTheDocument();
  });
});
