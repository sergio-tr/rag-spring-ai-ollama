import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(),
}));

vi.mock("@/features/lab/hooks/use-experimental-datasets", () => ({
  experimentalDatasetsQueryKey: ["lab", "experimental-datasets"],
  useExperimentalDatasetsQuery: vi.fn(),
  useUploadExperimentalDatasetMutation: vi.fn(() => ({
    mutateAsync: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  })),
}));

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";

describe("LabOverviewPage", () => {
  let LabOverviewPage: typeof import("./page").default;

  beforeEach(async () => {
    // Prevent accidental Next.js prefetch / fetch calls from hitting a real server in unit tests.
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 204 })));

    // Import after mocks are registered (prevents Next Link prefetch network calls in DOM test env).
    LabOverviewPage = (await import("./page")).default;
    vi.mocked(useLabStatus).mockReset();
    vi.mocked(useExperimentalDatasetsQuery).mockReset();
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
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

  it("shows typed reference workbook counts when backend reports dataset kinds ready", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        referenceBundleAvailable: true,
        referenceBundleValid: true,
        countsByDatasetKind: {
          llmReaderQuestions: 12,
          embeddingRetrievalQueries: 3,
          ragPresetQuestions: 5,
        },
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
    expect(screen.getByText(/LLM rows: 12/i)).toBeInTheDocument();
    expect(screen.queryByText(/legacy fallback/i)).not.toBeInTheDocument();
    expect(screen.getAllByText(/Internal reference workbook/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Packaged workbook present/i)).toBeInTheDocument();
  });

  it("shows simplified overview copy without endpoint-heavy primer text", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasetKindsReady: false,
        datasets: { enabled: false, datasetKindsReady: false },
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
    expect(screen.getByText(/operators should ship/i)).toBeInTheDocument();
  });

  it("renders the three TFG workflows with links to each evaluation page", () => {
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
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
    expect(screen.getByTestId("lab-tfg-control-panel")).toBeInTheDocument();
    expect(screen.getByText(/TFG control panel/i)).toBeInTheDocument();
    expect(screen.getByText(/Step 1/i)).toBeInTheDocument();
    expect(screen.getByText(/Step 2/i)).toBeInTheDocument();
    expect(screen.getByText(/Step 3/i)).toBeInTheDocument();
    expect(screen.getByText(/A\.\s*LLM model baseline/i)).toBeInTheDocument();
    expect(screen.getByText(/B\.\s*Embedding model baseline/i)).toBeInTheDocument();
    expect(screen.getByText(/C\.\s*RAG preset benchmark/i)).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Open workflow/i }).length).toBeGreaterThanOrEqual(3);
  });

  it("collapses technical server status lines behind a disclosure by default", async () => {
    const user = userEvent.setup();
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        countsByDatasetKind: { llmReaderQuestions: 1, embeddingRetrievalQueries: 1, ragPresetQuestions: 1 },
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
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        countsByDatasetKind: { llmReaderQuestions: 1, embeddingRetrievalQueries: 1, ragPresetQuestions: 1 },
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
