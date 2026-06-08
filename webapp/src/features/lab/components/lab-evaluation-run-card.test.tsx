import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ReactNode } from "react";
import { LabEvaluationRunCard } from "./lab-evaluation-run-card";

function LabEvalHarness({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <QueryClientProvider client={createTestQueryClient()}>
      <IntlTestProvider>{children}</IntlTestProvider>
    </QueryClientProvider>
  );
}

const { useEvaluationCorpusMock } = vi.hoisted(() => ({
  useEvaluationCorpusMock: vi.fn(),
}));

const defaultEvaluationCorpusApi = {
  summary: { documentCount: 2, readyCount: 2, documents: [] },
  effectiveCorpusId: "corpus-1",
  loading: false,
  fetching: false,
  error: null,
  refresh: vi.fn(),
  refreshAll: vi.fn(),
  ensureCorpus: vi.fn(),
  uploadDocuments: vi.fn(),
  corpusReady: true,
  corpusRunnable: true,
  corpusIndexReady: true,
  preparingIndex: false,
  readiness: { runnable: true, primaryBlocker: null, primaryBlockerMessage: null, reindexRequired: false, activeSnapshotId: "snap-1" },
  corpusProcessing: false,
  attachFromProject: vi.fn(),
  deleteDocument: vi.fn(),
  deleteAllDocuments: vi.fn(),
  retryDocumentIngest: vi.fn(),
  prepareIndex: vi.fn(),
};

vi.mock("@/features/lab/hooks/use-evaluation-corpus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/lab/hooks/use-evaluation-corpus")>();
  return {
    ...actual,
    useEvaluationCorpus: (...args: unknown[]) => useEvaluationCorpusMock(...args),
  };
});

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(),
}));

vi.mock("@/features/lab/hooks/use-experimental-datasets", () => ({
  useExperimentalDatasetsQuery: vi.fn(),
}));
vi.mock("@/features/lab/hooks/use-experimental-preset-catalog", () => ({
  useExperimentalPresetCatalog: vi.fn(),
}));

vi.mock("@/features/lab/hooks/use-active-lab-jobs", () => ({
  useActiveLabJobs: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-models-by-type", () => ({
  useModelsByType: vi.fn(),
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    selector({ activeProject: null }),
}));

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiProductPath: (p: string) => p,
  };
});

import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { useModelsByType } from "@/features/chat/hooks/use-models-by-type";
import { apiFetch } from "@/lib/api-client";

const llmDataset = {
  id: "550e8400-e29b-41d4-a716-446655440000",
  name: "ds",
  experimentalDatasetType: "LLM_MODEL_BASELINE",
  readOnly: false,
  datasetType: "LLM_ONLY",
  validationStatus: "VALID",
  questionCounts: { llmReaderQuestions: 2, embeddingQueries: 0, ragPresetQuestions: 0, presetCatalog: 0, chunkRegistry: 0 },
  isReferenceBundle: false,
  isDemoDataset: false,
  canRunLlmBaseline: true,
  canRunEmbeddingBaseline: false,
  canRunRagPresetBenchmark: false,
  validationIssues: [],
  uploadedAt: "2026-01-01T00:00:00Z",
  description: null,
};

const embeddingDataset = {
  id: "emb00000-0000-0000-0000-000000000001",
  name: "emb-ds",
  experimentalDatasetType: "EMBEDDING_MODEL_BASELINE",
  readOnly: false,
  datasetType: "EMBEDDING",
  validationStatus: "VALID",
  questionCounts: {
    llmReaderQuestions: 0,
    embeddingQueries: 4,
    ragPresetQuestions: 0,
    presetCatalog: 0,
    chunkRegistry: 0,
  },
  isReferenceBundle: false,
  isDemoDataset: false,
  canRunLlmBaseline: false,
  canRunEmbeddingBaseline: true,
  canRunRagPresetBenchmark: false,
  validationIssues: [],
  uploadedAt: "2026-01-01T00:00:00Z",
  description: null,
};

const ragDataset = {
  id: "ragguid-ragguid-ragguid-ragguid-000001",
  name: "rag-ds",
  experimentalDatasetType: "RAG_PRESET_BENCHMARK",
  readOnly: false,
  datasetType: "RAG",
  validationStatus: "VALID",
  questionCounts: {
    llmReaderQuestions: 0,
    embeddingQueries: 0,
    ragPresetQuestions: 4,
    presetCatalog: 15,
    chunkRegistry: 0,
  },
  isReferenceBundle: false,
  isDemoDataset: false,
  canRunLlmBaseline: false,
  canRunEmbeddingBaseline: false,
  canRunRagPresetBenchmark: true,
  validationIssues: [],
  uploadedAt: "2026-01-01T00:00:00Z",
  description: null,
};

function storedLlmDraft(overrides: Record<string, unknown>) {
  return JSON.stringify({
    v: 1,
    datasetId: llmDataset.id,
    explicitDraftClear: false,
    llmModelId: "",
    llmModelIds: [] as string[],
    embeddingModelId: "",
    embeddingModelIds: [] as string[],
    embeddingDownstreamRag: false,
    selectedExperimentalPresetCodes: [] as string[],
    runName: "",
    followMode: "poll",
    lastEvaluationRunId: null,
    corpusId: null,
    ...overrides,
  });
}

function storedRagDraft(overrides: Record<string, unknown>) {
  return JSON.stringify({
    v: 1,
    datasetId: ragDataset.id,
    explicitDraftClear: false,
    llmModelId: "",
    llmModelIds: [] as string[],
    embeddingModelId: "nomic-embed-test",
    embeddingModelIds: [] as string[],
    embeddingDownstreamRag: false,
    selectedExperimentalPresetCodes: Array.from({ length: 15 }, (_, i) => `P${i}`),
    runName: "",
    followMode: "poll",
    lastEvaluationRunId: null,
    corpusId: "corpus-1111-1111-1111-111111111111",
    ...overrides,
  });
}

function presetCodesFixture() {
  return Array.from({ length: 15 }, (_, i) => ({
    productPresetId: `preset-${i}`,
    code: `P${i}`,
    family: "baseline",
    label: `Preset ${i}`,
    description: "",
    requiredCapabilities: [] as string[],
    supported: true,
    supportStatus: i >= 13 ? "REQUIRES_MULTI_TURN" : "EXECUTABLE",
    reasonIfUnsupported: i >= 13 ? "FUTURE_MULTI_TURN_NOT_SELECTABLE" : null,
    requiresMultiTurn: i >= 13,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"] as const,
    chatSelectable: true,
    labSelectable: true,
    labOnly: false,
    corpusRequired: true,
    requiresSnapshot: true,
    requiresProjectDocuments: true,
    singleTurnBenchmarkSelectable: i <= 12,
    protocolStageIndex: i,
    parentPresetCode: i > 0 ? `P${i - 1}` : null,
    effectiveTerminalRuntimeJson: "{}",
  }));
}

describe("LabEvaluationRunCard", () => {
  beforeEach(() => {
    useEvaluationCorpusMock.mockReturnValue(defaultEvaluationCorpusApi);
    Object.keys(localStorage).forEach((k) => {
      if (k.startsWith("lab:evaluation-draft:v1:")) localStorage.removeItem(k);
    });
    vi.mocked(useLabStatus).mockReset();
    vi.mocked(useExperimentalDatasetsQuery).mockReset();
    vi.mocked(useExperimentalPresetCatalog).mockReset();
    vi.mocked(useActiveLabJobs).mockReset();
    vi.mocked(useLabStatus).mockReturnValue({
      data: {
        datasetKindsReady: true,
        datasets: { enabled: true, datasetKindsReady: true },
        evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
        classifier: { configured: true, train: true, evaluate: true },
        message: "",
      },
    } as never);
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [llmDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    vi.mocked(useExperimentalPresetCatalog).mockReturnValue({
      data: [
        ...presetCodesFixture(),
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000021",
          code: "PX_OBSOLETE",
          family: "conversational",
          label: "Clarification loop",
          description: "",
          indexRequirements: null,
          requiredCapabilities: [] as string[],
          supported: false,
          supportStatus: "REQUIRES_MULTI_TURN",
          reasonIfUnsupported: "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED",
          requiresMultiTurn: true,
          mapsToRuntimeCapabilities: {},
          allowedOutcomes: ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"] as const,
          chatSelectable: false,
          labSelectable: true,
          labOnly: true,
          corpusRequired: true,
          requiresSnapshot: true,
          requiresProjectDocuments: true,
          singleTurnBenchmarkSelectable: false,
          protocolStageIndex: 13,
          parentPresetCode: "P12",
          effectiveTerminalRuntimeJson: "{}",
        },
      ],
      isSuccess: true,
    } as never);
    vi.mocked(useActiveLabJobs).mockReturnValue({ data: [], isLoading: false, isFetched: true, isError: false } as never);
    vi.mocked(useModelsByType).mockImplementation(((type: string) => ({
      data:
        type === "LLM"
          ? [
              { modelId: "llama:judge", displayName: "llama:judge", type: "LLM" },
              { modelId: "llama:fast", displayName: "llama:fast", type: "LLM" },
              { modelId: "llama:quality", displayName: "llama:quality", type: "LLM" },
            ]
          : [
              { modelId: "nomic-embed-test", displayName: "nomic-embed-test", type: "EMBEDDING" },
              { modelId: "qwen3-embedding:latest", displayName: "qwen3-embedding", type: "EMBEDDING" },
            ],
      isLoading: false,
      isSuccess: true,
    })) as never);
    vi.mocked(apiFetch).mockReset();
  });

  it("shows user-facing description without HTTP 202 jargon on the card surface", () => {
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByText(/Benchmark the configured LLM/i)).toBeInTheDocument();
    expect(screen.queryByText(/HTTP 202/i)).not.toBeInTheDocument();
  });

  it("shows dataset-unavailable warning when no compatible experimental dataset exists", () => {
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-benchmark-needs-dataset-warn")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run evaluation/i })).toBeDisabled();
  });

  it("disables run when the selected dataset is INVALID", () => {
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [{ ...llmDataset, id: "bad", validationStatus: "INVALID" }],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-selected-dataset-details")).toBeInTheDocument();
    expect(screen.getByTestId("lab-dataset-invalid-warn")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run evaluation/i })).toBeDisabled();
  });

  it("keeps benchmark kind label inside technical disclosure by default", async () => {
    const user = userEvent.setup();
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </LabEvalHarness>,
    );
    const developerDetails = screen.getByTestId("lab-eval-technical-details");
    expect(developerDetails).not.toHaveAttribute("open");
    expect(screen.queryByText(/LLM_JUDGE_QA/i)).not.toBeInTheDocument();
    await user.click(screen.getByText(/Technical details/i));
    expect(screen.getByTestId("lab-eval-benchmark-kind-label")).toHaveTextContent(/LLM evaluation/i);
    expect(screen.queryByText(/POST \/api/i)).not.toBeInTheDocument();
  });

  it("sanitizes saved P13/P14 from draft on load and shows product message", async () => {
    localStorage.setItem(
      "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END",
      storedRagDraft({
        selectedExperimentalPresetCodes: ["P0", "P13", "P14"],
      }),
    );
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [ragDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-test-rag-sanitize"
        />
      </LabEvalHarness>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("lab-draft-presets-sanitized")).toHaveTextContent(
        /not available for this evaluation type and were removed/i,
      );
    });
    expect(screen.queryByText(/FUTURE_MULTI_TURN_NOT_SELECTABLE/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/cannot run in this evaluation/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-experimental-preset-P0")).toBeChecked();
    expect(screen.getByTestId("lab-experimental-preset-P13")).not.toBeChecked();
    expect(screen.getByTestId("lab-experimental-preset-P13")).toBeDisabled();
  });

  it("disables non-lab-selectable presets when only P13 remains after sanitation", async () => {
    localStorage.setItem(
      "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END",
      JSON.stringify({
        v: 1,
        datasetId: "ds-llm",
        selectedExperimentalPresetCodes: ["P13"],
        corpusId: "corpus-1111-1111-1111-111111111111",
      }),
    );
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-test-rag"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-experimental-preset-P13")).toBeDisabled();
    expect(screen.getByTestId("lab-rag-run")).toBeDisabled();
    expect(screen.getByTestId("lab-draft-presets-sanitized")).toHaveTextContent(
      /not available for this evaluation type and were removed/i,
    );
    expect(screen.queryByText(/evalDraftWarnPresetsNotLabSelectable|cannot run in this evaluation/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/FUTURE_MULTI_TURN_NOT_SELECTABLE/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/REQUIRES_MULTI_TURN/i)).not.toBeInTheDocument();
  });

  it("shows experimental preset catalog in RAG benchmark mode without long unsupported reasons visible", () => {
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-test-rag"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-experimental-presets-list")).toBeInTheDocument();
    expect(screen.getByText(/PX_OBSOLETE — Clarification loop/i)).toBeInTheDocument();
    expect(screen.getByTestId("lab-preset-blocked-PX_OBSOLETE")).toBeInTheDocument();
    expect(screen.getByTestId("lab-preset-blocked-PX_OBSOLETE")).toHaveTextContent(
      /not available for this evaluation type/i,
    );
    expect(screen.queryByText(/PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-experimental-presets-select-core")).toBeInTheDocument();
  });

  it("blocks running when dataset is marked as demo", () => {
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [
        {
          ...llmDataset,
          id: "demo",
          validationStatus: "VALID",
          isDemoDataset: true,
          canRunLlmBaseline: true,
          questionCounts: { llmReaderQuestions: 36, embeddingQueries: 0, ragPresetQuestions: 0, presetCatalog: 0, chunkRegistry: 0 },
        },
      ],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-dataset-blocked-demo")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run evaluation/i })).toBeDisabled();
  });

  it("disables Run when another active lab job exists", () => {
    vi.mocked(useActiveLabJobs).mockReturnValue({
      data: [
        {
          jobId: "job-active",
          benchmarkKind: "RAG_PRESET_END_TO_END",
          evaluationRunId: "run-active",
          projectId: null,
          datasetId: null,
          status: "RUNNING",
          progress: "Running…",
          startedAt: null,
          updatedAt: null,
          pollPath: "/lab/jobs/job-active",
          streamPath: "/lab/jobs/job-active/events",
          cancellable: true,
        },
      ],
      isLoading: false,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-test"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByRole("button", { name: /Run evaluation/i })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(/already running/i);
  });

  it("restores dataset, models, and run name from versioned localStorage after remount (refresh simulation)", () => {
    localStorage.setItem(
      "lab:evaluation-draft:v1:LLM_JUDGE_QA",
      storedLlmDraft({
        datasetId: llmDataset.id,
        llmModelIds: ["llama:judge"],
        runName: "Regression May",
      }),
    );
    const first = render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-draft"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-eval-run-name")).toHaveValue("Regression May");
    expect(screen.getByTestId("lab-benchmark-dataset-select")).toHaveValue(llmDataset.id);
    expect(screen.getByTestId("lab-benchmark-llm-models-llama:judge")).toBeChecked();

    first.unmount();

    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-draft-2"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-eval-run-name")).toHaveValue("Regression May");
    expect(screen.getByTestId("lab-benchmark-dataset-select")).toHaveValue(llmDataset.id);
    expect(screen.getByTestId("lab-benchmark-llm-models-llama:judge")).toBeChecked();
  });

  it("restores RAG preset picks P0–P12 from localStorage and sanitizes P13/P14", () => {
    localStorage.setItem("lab:evaluation-draft:v1:RAG_PRESET_END_TO_END", storedRagDraft({}));
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [ragDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-draft-rag"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-draft-presets-sanitized")).toBeInTheDocument();
    for (let i = 0; i <= 12; i += 1) {
      expect(screen.getByTestId(`lab-experimental-preset-P${i}`)).toBeChecked();
    }
    expect(screen.getByTestId("lab-experimental-preset-P13")).not.toBeChecked();
    expect(screen.getByTestId("lab-experimental-preset-P14")).not.toBeChecked();
  });

  it("disables Run when corpus readiness reports NO_DOCUMENTS blocker", () => {
    useEvaluationCorpusMock.mockReturnValue({
      ...defaultEvaluationCorpusApi,
      corpusRunnable: false,
      corpusIndexReady: false,
      readiness: {
        runnable: false,
        primaryBlocker: "NO_DOCUMENTS",
        primaryBlockerMessage: "The knowledge base has no documents.",
      },
    });
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [ragDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    localStorage.setItem("lab:evaluation-draft:v1:RAG_PRESET_END_TO_END", storedRagDraft({}));
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-corpus-blocked"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-corpus-not-ready-hint")).toBeInTheDocument();
    expect(screen.getByTestId("lab-rag-run")).toBeDisabled();
  });

  it("enables Run when corpus index is ready and runnable", () => {
    useEvaluationCorpusMock.mockReturnValue({
      ...defaultEvaluationCorpusApi,
      corpusRunnable: true,
      corpusIndexReady: true,
      corpusReady: true,
      readiness: {
        runnable: true,
        reindexRequired: false,
        activeSnapshotId: "snap-1",
        primaryBlocker: null,
        primaryBlockerMessage: null,
      },
    });
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [ragDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    localStorage.setItem("lab:evaluation-draft:v1:RAG_PRESET_END_TO_END", storedRagDraft({}));
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-corpus-ready"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-rag-run")).toBeEnabled();
    expect(screen.queryByTestId("lab-corpus-not-ready-hint")).not.toBeInTheDocument();
  });

  it("enables Run when index is missing but documents are runnable", () => {
    useEvaluationCorpusMock.mockReturnValue({
      ...defaultEvaluationCorpusApi,
      corpusRunnable: true,
      corpusIndexReady: false,
      corpusReady: true,
      readiness: {
        runnable: true,
        reindexRequired: true,
        activeSnapshotId: null,
        snapshotBlocker: "INDEX_PREPARATION_REQUIRED",
        primaryBlocker: null,
        primaryBlockerMessage: null,
      },
    });
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [ragDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    localStorage.setItem("lab:evaluation-draft:v1:RAG_PRESET_END_TO_END", storedRagDraft({}));
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-corpus-index-blocked"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-rag-run")).toBeEnabled();
    expect(screen.queryByTestId("lab-corpus-index-hint")).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-corpus-index-will-prepare")).toBeInTheDocument();
  });

  it("shows evaluation corpus panel for RAG without active project", () => {
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [ragDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          cardDescription="Benchmark retrieval presets."
          runButtonTestId="lab-rag-run"
          radioGroupName="follow-corpus-rag"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-evaluation-corpus-panel")).toBeInTheDocument();
    expect(screen.queryByText(/No active project selected/i)).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-corpus-import-hint")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Select an active project before running a RAG preset benchmark/i),
    ).not.toBeInTheDocument();
  });

  it("shows evaluation corpus panel for embedding without active project", () => {
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [embeddingDataset],
      isLoading: false,
      isFetched: true,
      isSuccess: true,
    } as never);
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="EMBEDDING_RETRIEVAL"
          sectionKey="evaluation-embedding"
          taskTypeHint="EMBEDDING_EVALUATION"
          cardTitle="Embedding evaluation"
          cardDescription="Compare embedding models."
          runButtonTestId="lab-embedding-run"
          radioGroupName="follow-corpus-embedding"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-evaluation-corpus-panel")).toBeInTheDocument();
    expect(screen.queryByText(/No active project selected/i)).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-corpus-import-hint")).not.toBeInTheDocument();
  });

  it("shows draft warning when stored dataset id no longer exists", () => {
    localStorage.setItem(
      "lab:evaluation-draft:v1:LLM_JUDGE_QA",
      storedLlmDraft({ datasetId: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }),
    );
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-missing-ds"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-evaluation-draft-warnings")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run evaluation/i })).toBeDisabled();
  });

  it("shows comparison button label and submits llmModelIds array when multiple models selected", async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      "lab:evaluation-draft:v1:LLM_JUDGE_QA",
      storedLlmDraft({
        llmModelIds: ["llama:judge", "llama:fast", "llama:quality"],
        corpusId: "corpus-1111-1111-1111-111111111111",
      }),
    );
    vi.mocked(apiFetch).mockResolvedValue({
      evaluationRunId: "run-0000-0000-0000-000000000001",
      asyncTaskId: "job-0000-0000-0000-000000000001",
      campaignId: "camp-0000-0000-0000-000000000001",
    });

    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-campaign"
        />
      </LabEvalHarness>,
    );

    expect(screen.getByRole("button", { name: /Run model comparison/i })).toBeInTheDocument();
    expect(screen.getByTestId("lab-comparison-selection-hint")).toHaveTextContent(/Comparing 3 models/i);

    await user.click(screen.getByTestId("lab-llm-run"));

    expect(vi.mocked(apiFetch)).toHaveBeenCalled();
    const call = vi.mocked(apiFetch).mock.calls.find((c) => {
      const url = String(c[0] ?? "");
      const method = (c[1] as RequestInit | undefined)?.method ?? "GET";
      return url.includes("/lab/benchmarks/LLM_JUDGE_QA/runs") && !url.includes("/runs/latest") && method === "POST";
    });
    expect(call).toBeTruthy();
    const init = call?.[1] as RequestInit | undefined;
    const body = JSON.parse(String(init?.body ?? "{}")) as { llmModelIds?: string[]; campaignName?: string };
    expect(body.llmModelIds).toEqual(["llama:judge", "llama:fast", "llama:quality"]);
    expect(body.campaignName).toBeTruthy();
  });

  describe("EMBEDDING_RETRIEVAL model compatibility", () => {
    beforeEach(() => {
      useEvaluationCorpusMock.mockReturnValue({
        ...defaultEvaluationCorpusApi,
        effectiveCorpusId: "corpus-emb",
      });
      vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
        data: [embeddingDataset],
        isLoading: false,
        isFetched: true,
        isSuccess: true,
      } as never);
    });

    it("shows concise blocked message when only one compatible embedding is available", () => {
      vi.mocked(useModelsByType).mockImplementation(((type: string) => ({
        data:
          type === "EMBEDDING"
            ? [{ modelId: "mxbai-embed-large:latest", displayName: "mxbai", type: "EMBEDDING" }]
            : [],
        isLoading: false,
        isSuccess: true,
      })) as never);

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-one"
          />
        </LabEvalHarness>,
      );

      const blocked = screen.getByTestId("lab-embedding-model-availability-blocked");
      expect(blocked).toHaveTextContent(
        "At least two compatible embedding models are required for comparison.",
      );
      expect(blocked.textContent).not.toMatch(/Missing preferred/i);
      expect(blocked.textContent).not.toMatch(/bge-m3/i);
      expect(blocked.textContent).not.toMatch(/EMBEDDING_CAMPAIGN_STORE_DIMENSION/);
    });

    it("does not offer incompatible embedding tags in the checkbox group", () => {
      vi.mocked(useModelsByType).mockImplementation(((type: string) => ({
        data:
          type === "EMBEDDING"
            ? [
                { modelId: "mxbai-embed-large:latest", displayName: "mxbai", type: "EMBEDDING" },
                { modelId: "nomic-embed-text:latest", displayName: "nomic", type: "EMBEDDING" },
                { modelId: "qwen3-embedding:latest", displayName: "qwen3", type: "EMBEDDING" },
              ]
            : [],
        isLoading: false,
        isSuccess: true,
      })) as never);

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-filter"
          />
        </LabEvalHarness>,
      );

      expect(screen.getByTestId("lab-benchmark-embedding-models-mxbai-embed-large:latest")).toBeInTheDocument();
      expect(screen.queryByTestId("lab-benchmark-embedding-models-nomic-embed-text:latest")).not.toBeInTheDocument();
      expect(screen.queryByTestId("lab-benchmark-embedding-models-qwen3-embedding:latest")).not.toBeInTheDocument();
    });
  });
});
