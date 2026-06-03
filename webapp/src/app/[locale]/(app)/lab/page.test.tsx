import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider, type UseQueryResult } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ExperimentalDatasetListItemDto, LabStatusResponse } from "@/types/api";

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={`/en${href}`}>{children}</a>
  ),
  usePathname: () => "/lab",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
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

type QueryMock<T> = Partial<UseQueryResult<T, Error>> & {
  data: T | undefined;
  isLoading: boolean;
  isError: boolean;
};

function queryMock<T>(value: QueryMock<T>): UseQueryResult<T, Error> {
  return {
    refetch: vi.fn(),
    ...value,
  } as unknown as UseQueryResult<T, Error>;
}

describe("LabOverviewPage", () => {
  let LabOverviewPage: typeof import("./page").default;

  beforeEach(async () => {
    // Prevent accidental Next.js prefetch / fetch calls from hitting a real server in unit tests.
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 204 })));

    // Import after mocks are registered (prevents Next Link prefetch network calls in DOM test env).
    LabOverviewPage = (await import("./page")).default;
    vi.mocked(useLabStatus).mockReset();
    vi.mocked(useExperimentalDatasetsQuery).mockReset();
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue(queryMock<ExperimentalDatasetListItemDto[]>({
      data: [],
      isLoading: false,
      isError: false,
    }));
  });

  it("shows error state without throwing when status fetch fails", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
      data: undefined,
      isError: true,
      isLoading: false,
      refetch: vi.fn(),
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/Could not load lab status/i);
  });

  it("renders dataset overview table with counts and demo badge when datasets are present", async () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        referenceBundleAvailable: true,
        referenceBundleValid: true,
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    }));
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue(queryMock<ExperimentalDatasetListItemDto[]>({
      data: [
        {
          id: "550e8400-e29b-41d4-a716-446655440000",
          name: "Demo dataset",
          experimentalDatasetType: "RAG_PRESET_BENCHMARK",
          readOnly: false,
          datasetType: "RAG",
          validationStatus: "INVALID",
          questionCounts: { llmReaderQuestions: 0, embeddingQueries: 0, ragPresetQuestions: 1, presetCatalog: 1, chunkRegistry: 0 },
          isReferenceBundle: false,
          isDemoDataset: true,
          canRunLlmBaseline: false,
          canRunEmbeddingBaseline: false,
          canRunRagPresetBenchmark: false,
          validationIssues: [],
          uploadedAt: "",
          description: null,
        },
      ],
      isLoading: false,
      isError: false,
    }));

    LabOverviewPage = (await import("./page")).default;
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId("lab-dataset-overview-table")).toBeInTheDocument();
    expect(screen.getAllByText(/Demo dataset/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/DEMO/i).length).toBeGreaterThanOrEqual(1);
  });

  it("shows typed reference workbook counts when backend reports dataset kinds ready", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
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
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/LLM rows: 12/i)).toBeInTheDocument();
    expect(screen.queryByText(/obsolete fallback/i)).not.toBeInTheDocument();
    expect(screen.getAllByText(/Internal reference workbook/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Packaged workbook present/i)).toBeInTheDocument();
  });

  it("shows simplified overview copy without endpoint-heavy primer text", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
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
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/Pick a workflow/i)).toBeInTheDocument();
    expect(screen.queryByText(/Feature flags from GET/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\{product\}/i)).not.toBeInTheDocument();
    expect(screen.getByText(/packaged reference workbook is missing/i)).toBeInTheDocument();
  });

  it("renders locale-aware links for the datasets anchor", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        referenceBundleAvailable: true,
        referenceBundleValid: true,
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    }));

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    const link = screen.getByRole("link", { name: /Go to templates & uploads/i });
    expect(link).toHaveAttribute("href", "/en#datasets");
  });

  it("renders four compact workflow cards with links", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
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
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId("lab-overview-workflow-cards")).toBeInTheDocument();
    expect(screen.getByTestId("lab-workflow-card-llm")).toBeInTheDocument();
    expect(screen.getByTestId("lab-workflow-card-embedding")).toBeInTheDocument();
    expect(screen.getByTestId("lab-workflow-card-rag")).toBeInTheDocument();
    expect(screen.getByTestId("lab-workflow-card-classifier")).toBeInTheDocument();
    expect(screen.queryByText(/Follow the steps below/i)).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Open workflow/i }).length).toBeGreaterThanOrEqual(4);
  });

  it("collapses technical server status lines behind a disclosure by default", async () => {
    const user = userEvent.setup();
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        countsByDatasetKind: { llmReaderQuestions: 1, embeddingRetrievalQueries: 1, ragPresetQuestions: 1 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message:
          "Research Lab is ready. Pick a workbook on the overview or evaluation pages and run a benchmark.",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const technical = screen.getByTestId("lab-overview-status-technical");
    expect(technical).not.toHaveAttribute("open");
    const serverDetails = screen.getByText(/Server note/i).closest("details");
    expect(serverDetails).not.toHaveAttribute("open");
    expect(serverDetails).toHaveTextContent(/Research Lab is ready/i);
    await user.click(screen.getByText(/Technical details/i));
    expect(technical).toHaveAttribute("open");
    await user.click(screen.getByText(/Server note/i));
    expect(serverDetails).toHaveAttribute("open");
  });

  it("does not surface forbidden technical API copy on the overview when status loads", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        countsByDatasetKind: { llmReaderQuestions: 1, embeddingRetrievalQueries: 1, ragPresetQuestions: 1 },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message:
          "Research Lab is ready. Pick a workbook on the overview or evaluation pages and run a benchmark.",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.queryByText(/Lab API —/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/POST \/api/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/canonical benchmarks/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Status poll:/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Live stream:/i)).not.toBeInTheDocument();
  });

  it("does not surface compose observability filenames in the Lab overview cards", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
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
    }));
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabOverviewPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId("lab-overview-compact")).toBeInTheDocument();
    expect(screen.getByText("LLM & RAG evaluations")).toBeInTheDocument();
    expect(screen.queryByText(/compose\.obs\.yml/i)).not.toBeInTheDocument();
  });

  it("shows loading copy when status is loading", () => {
    vi.mocked(useLabStatus).mockReturnValue(queryMock<LabStatusResponse>({
      data: undefined,
      isError: false,
      isLoading: true,
      refetch: vi.fn(),
    }));
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
