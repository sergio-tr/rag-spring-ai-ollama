import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
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
