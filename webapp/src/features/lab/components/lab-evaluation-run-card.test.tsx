import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ReactNode } from "react";
import { LabEvaluationRunCard } from "./lab-evaluation-run-card";
import type { LabEvaluationModelDto } from "@/types/api";

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

vi.mock("@/features/lab/hooks/use-lab-evaluation-models", () => ({
  useLabEvaluationModels: vi.fn(),
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
import { useLabEvaluationModels } from "@/features/lab/hooks/use-lab-evaluation-models";
import { apiFetch } from "@/lib/api-client";

const chatCatalogFixture = [
  {
    modelName: "llama:judge",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "AVAILABLE" as const,
    embeddingDimensions: null,
    compatibleWithCurrentVectorStore: null,
    usableAsDefault: true,
  },
  {
    modelName: "llama:fast",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "AVAILABLE" as const,
    embeddingDimensions: null,
    compatibleWithCurrentVectorStore: null,
    usableAsDefault: false,
  },
  {
    modelName: "llama:quality",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "AVAILABLE" as const,
    embeddingDimensions: null,
    compatibleWithCurrentVectorStore: null,
    usableAsDefault: false,
  },
];

const embeddingCatalogFixture = [
  {
    modelName: "mxbai-embed:latest",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "AVAILABLE" as const,
    embeddingDimensions: 1024,
    compatibleWithCurrentVectorStore: true,
    usableAsDefault: true,
  },
  {
    modelName: "nomic-embed-test",
    evalSelectable: false,
    blockedReason: "Incompatible with vector store",
    blockedReasonCode: "EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE",
    runtimeStatus: "AVAILABLE" as const,
    embeddingDimensions: 768,
    compatibleWithCurrentVectorStore: false,
    usableAsDefault: false,
  },
];

function mockEvaluationCatalogs(
  chatModels: LabEvaluationModelDto[] = chatCatalogFixture,
  embeddingModels: LabEvaluationModelDto[] = embeddingCatalogFixture,
) {
  vi.mocked(useLabEvaluationModels).mockImplementation(((capability: string) => ({
    data:
      capability === "CHAT"
        ? {
            effectiveProvider: "OLLAMA_NATIVE",
            capability: "CHAT",
            models: chatModels,
            hasCompatibleEmbeddingModels: embeddingModels.some((m) => m.compatibleWithCurrentVectorStore === true),
          }
        : {
            effectiveProvider: "OLLAMA_NATIVE",
            capability: "EMBEDDING",
            models: embeddingModels,
            hasCompatibleEmbeddingModels: embeddingModels.some((m) => m.compatibleWithCurrentVectorStore === true),
          },
    isLoading: false,
    isSuccess: true,
    isError: false,
  })) as never);
}

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

function storedEmbeddingDraft(overrides: Record<string, unknown>) {
  return JSON.stringify({
    v: 1,
    datasetId: embeddingDataset.id,
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
    corpusId: "corpus-emb",
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
    embeddingModelId: "mxbai-embed:latest",
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
    supportStatus:
      i >= 13 ? "FUTURE_MULTI_TURN_NOT_SELECTABLE" : i >= 11 ? "NOT_COMPARABLE_IN_SINGLE_TURN_LAB" : "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: i >= 13,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"] as const,
    chatSelectable: true,
    labSelectable: i <= 10,
    labOnly: false,
    corpusRequired: true,
    requiresSnapshot: true,
    requiresProjectDocuments: true,
    singleTurnBenchmarkSelectable: i <= 10,
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
    mockEvaluationCatalogs();
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
    expect(screen.getByRole("button", { name: /Run evaluation|Evaluate selected model/i })).toBeDisabled();
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
    expect(screen.getByRole("button", { name: /Run evaluation|Evaluate selected model/i })).toBeDisabled();
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
    await user.click(screen.getByText(/Advanced technical details/i));
    expect(screen.getByTestId("lab-eval-benchmark-kind-label")).toHaveTextContent(/Chat model evaluation/i);
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
    expect(screen.getByText(/Clarification loop/i)).toBeInTheDocument();
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
    expect(screen.getByRole("button", { name: /Run evaluation|Evaluate selected model/i })).toBeDisabled();
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
    expect(screen.getByRole("button", { name: /Run evaluation|Evaluate selected model/i })).toBeDisabled();
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

  it("does not render misleading chat model tag combobox on LLM evaluation", () => {
    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-run"
          radioGroupName="follow-no-tag-combo"
        />
      </LabEvalHarness>,
    );
    expect(screen.getByTestId("lab-benchmark-llm-models-group")).toBeInTheDocument();
    expect(screen.queryByTestId("lab-benchmark-llm-model")).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Chat model tag/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Campaign tag/i)).toBeInTheDocument();
  });

  it("restores RAG preset picks P0–P10 from localStorage and sanitizes P11–P14", () => {
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
    for (let i = 0; i <= 10; i += 1) {
      expect(screen.getByTestId(`lab-experimental-preset-P${i}`)).toBeChecked();
    }
    for (let i = 11; i <= 14; i += 1) {
      expect(screen.getByTestId(`lab-experimental-preset-P${i}`)).not.toBeChecked();
    }
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
    expect(screen.getByRole("button", { name: /Run evaluation|Evaluate selected model/i })).toBeDisabled();
  });

  it("submits single llmModelId from model comparison checkboxes for LLM judge evaluation", async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      "lab:evaluation-draft:v1:LLM_JUDGE_QA",
      storedLlmDraft({
        llmModelIds: ["llama:judge"],
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

    expect(screen.getByTestId("lab-benchmark-llm-models-group")).toBeInTheDocument();
    expect(screen.getByTestId("lab-benchmark-llm-models-llama:judge")).toBeChecked();

    await user.click(screen.getByTestId("lab-llm-run"));

    const call = vi.mocked(apiFetch).mock.calls.find((c) => {
      const url = String(c[0] ?? "");
      const method = (c[1] as RequestInit | undefined)?.method ?? "GET";
      return url.includes("/lab/benchmarks/LLM_JUDGE_QA/runs") && !url.includes("/runs/latest") && method === "POST";
    });
    expect(call).toBeTruthy();
    const init = call?.[1] as RequestInit | undefined;
    const body = JSON.parse(String(init?.body ?? "{}")) as { llmModelId?: string; llmModelIds?: string[] };
    expect(body.llmModelId).toBe("llama:judge");
    expect(body.llmModelIds).toBeUndefined();
  });

  it("shows evaluate-selected label and submits llmModelId for single model selection", async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      "lab:evaluation-draft:v1:LLM_JUDGE_QA",
      storedLlmDraft({
        llmModelIds: ["llama:judge"],
        corpusId: "corpus-1111-1111-1111-111111111111",
      }),
    );
    vi.mocked(apiFetch).mockResolvedValue({
      evaluationRunId: "run-0000-0000-0000-000000000002",
      asyncTaskId: "job-0000-0000-0000-000000000002",
    });

    render(
      <LabEvalHarness>
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          cardDescription="Benchmark the configured LLM against loaded evaluation questions."
          runButtonTestId="lab-llm-single-run"
          radioGroupName="follow-single-model"
        />
      </LabEvalHarness>,
    );

    expect(screen.getByRole("button", { name: /Evaluate selected model/i })).toBeInTheDocument();
    expect(screen.getByTestId("lab-comparison-selection-hint")).toHaveTextContent(/Evaluate selected model/i);
    expect(screen.queryByTestId("lab-llm-model-availability-blocked")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("lab-llm-single-run"));

    const call = vi.mocked(apiFetch).mock.calls.find((c) => {
      const url = String(c[0] ?? "");
      const method = (c[1] as RequestInit | undefined)?.method ?? "GET";
      return url.includes("/lab/benchmarks/LLM_JUDGE_QA/runs") && method === "POST";
    });
    expect(call).toBeTruthy();
    const body = JSON.parse(String((call?.[1] as RequestInit | undefined)?.body ?? "{}")) as {
      llmModelId?: string;
      llmModelIds?: string[];
      campaignName?: string;
    };
    expect(body.llmModelId).toBe("llama:judge");
    expect(body.llmModelIds).toBeUndefined();
    expect(body.campaignName).toBeUndefined();
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

    it("does not block single-model embedding evaluation when only one compatible model exists", async () => {
      mockEvaluationCatalogs([], [
        {
          modelName: "mxbai-embed-large:latest",
          evalSelectable: true,
          blockedReason: null,
    blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: true,
        },
      ]);

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

      expect(screen.queryByTestId("lab-embedding-model-availability-blocked")).not.toBeInTheDocument();
      await waitFor(() => expect(screen.getByTestId("lab-embedding-run")).toBeEnabled());
    });

    it("enables embedding run when corpus needs reindex but documents are runnable", async () => {
      useEvaluationCorpusMock.mockReturnValue({
        ...defaultEvaluationCorpusApi,
        effectiveCorpusId: "corpus-emb",
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
      localStorage.setItem(
        "lab:evaluation-draft:v1:EMBEDDING_RETRIEVAL",
        storedEmbeddingDraft({ corpusId: "corpus-emb" }),
      );

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-reindex"
          />
        </LabEvalHarness>,
      );

      await waitFor(() => expect(screen.getByTestId("lab-embedding-run")).toBeEnabled());
      expect(screen.getByTestId("lab-corpus-index-will-prepare")).toBeInTheDocument();
    });

    it("shows disabled reason when no embedding model is selected", async () => {
      mockEvaluationCatalogs([], []);
      localStorage.setItem(
        "lab:evaluation-draft:v1:EMBEDDING_RETRIEVAL",
        storedEmbeddingDraft({
          embeddingModelId: "",
          embeddingModelIds: [],
        }),
      );

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-no-model"
          />
        </LabEvalHarness>,
      );

      await waitFor(() => expect(screen.getByTestId("lab-embedding-run")).toBeDisabled());
      expect(screen.getByTestId("lab-eval-run-disabled-reason")).toHaveTextContent(
        /Select at least one compatible embedding model/i,
      );
    });

    it("submits embeddingModelIds on run click", async () => {
      const user = userEvent.setup();
      mockEvaluationCatalogs([], [
        {
          modelName: "hf-embed-a",
          evalSelectable: true,
          blockedReason: null,
          blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: true,
        },
        {
          modelName: "hf-embed-b",
          evalSelectable: true,
          blockedReason: null,
          blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: false,
        },
      ]);
      localStorage.setItem(
        "lab:evaluation-draft:v1:EMBEDDING_RETRIEVAL",
        storedEmbeddingDraft({
          embeddingModelIds: ["hf-embed-a", "hf-embed-b"],
          corpusId: "corpus-emb",
        }),
      );
      vi.mocked(apiFetch).mockResolvedValue({
        evaluationRunId: "run-emb-1",
        asyncTaskId: "job-emb-1",
      });

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-submit"
          />
        </LabEvalHarness>,
      );

      await waitFor(() => expect(screen.getByTestId("lab-embedding-run")).toBeEnabled());
      await user.click(screen.getByTestId("lab-embedding-run"));

      const call = vi.mocked(apiFetch).mock.calls.find((c) => {
        const url = String(c[0] ?? "");
        const method = (c[1] as RequestInit | undefined)?.method ?? "GET";
        return url.includes("/lab/benchmarks/EMBEDDING_RETRIEVAL/runs") && method === "POST";
      });
      expect(call).toBeTruthy();
      const body = JSON.parse(String((call?.[1] as RequestInit | undefined)?.body ?? "{}")) as {
        embeddingModelIds?: string[];
        llmModelIds?: string[];
        autoReindex?: boolean;
      };
      expect(body.embeddingModelIds).toEqual(["hf-embed-a", "hf-embed-b"]);
      expect(body.llmModelIds).toBeUndefined();
      expect(body.autoReindex).toBe(true);
    });

    it("shows blocked message when comparison selects more models than catalog offers", () => {
      localStorage.setItem(
        "lab:evaluation-draft:v1:EMBEDDING_RETRIEVAL",
        storedEmbeddingDraft({
          embeddingModelIds: ["hf-embed-a", "hf-embed-b"],
        }),
      );
      mockEvaluationCatalogs([], [
        {
          modelName: "hf-embed-a",
          evalSelectable: true,
          blockedReason: null,
          blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: true,
        },
      ]);

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-two"
          />
        </LabEvalHarness>,
      );

      const blocked = screen.getByTestId("lab-embedding-model-availability-blocked");
      expect(blocked).toHaveTextContent(
        "At least two compatible embedding models are required for comparison.",
      );
    });

    it("shows retrieval parameters and embedding comparison section without a global embedding hyperparameter", async () => {
      mockEvaluationCatalogs([], [
        {
          modelName: "bge-m3",
          evalSelectable: true,
          blockedReason: null,
          blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: true,
        },
        {
          modelName: "snowflake-arctic-embed2",
          evalSelectable: true,
          blockedReason: null,
          blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: false,
        },
      ]);
      localStorage.setItem(
        "lab:evaluation-draft:v1:EMBEDDING_RETRIEVAL",
        storedEmbeddingDraft({
          embeddingModelIds: ["bge-m3", "snowflake-arctic-embed2"],
        }),
      );

      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            cardDescription="Compare embedding models."
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-ui"
          />
        </LabEvalHarness>,
      );

      await waitFor(() => expect(screen.getByText("Embedding evaluation parameters")).toBeInTheDocument());
      expect(screen.getByRole("group", { name: "Embedding models to compare" })).toBeInTheDocument();
      expect(screen.queryByTestId("lab-hp-embedding-model")).not.toBeInTheDocument();
      expect(screen.getByTestId("embedding-evaluator-options-form")).toBeInTheDocument();
      expect(screen.getByTestId("lab-hp-top-k")).toBeInTheDocument();
      expect(screen.getByTestId("lab-comparison-selection-hint")).toHaveTextContent(
        "Comparing 2 embedding models",
      );
    });

    it("does not offer incompatible embedding tags in the checkbox group", () => {
      mockEvaluationCatalogs([], [
        {
          modelName: "mxbai-embed-large:latest",
          evalSelectable: true,
          blockedReason: null,
    blockedReasonCode: null,
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 1024,
          compatibleWithCurrentVectorStore: true,
          usableAsDefault: true,
        },
        {
          modelName: "nomic-embed-text:latest",
          evalSelectable: false,
          blockedReason: "Incompatible with vector store",
    blockedReasonCode: "EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE",
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: 768,
          compatibleWithCurrentVectorStore: false,
          usableAsDefault: false,
        },
        {
          modelName: "qwen3-embedding:latest",
          evalSelectable: false,
          blockedReason: "Incompatible with vector store",
    blockedReasonCode: "EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE",
          runtimeStatus: "AVAILABLE",
          embeddingDimensions: null,
          compatibleWithCurrentVectorStore: false,
          usableAsDefault: false,
        },
      ]);

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

  describe("evaluation route parameter visibility", () => {
    function renderRagRouteCard() {
      return render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="RAG_PRESET_END_TO_END"
            sectionKey="evaluation-rag"
            taskTypeHint="RAG_EVALUATION"
            cardTitle="Run"
            runButtonTestId="lab-rag-run"
            radioGroupName="follow-rag-route"
          />
        </LabEvalHarness>,
      );
    }

    beforeEach(() => {
      vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
        data: [ragDataset],
        isLoading: false,
        isFetched: true,
        isSuccess: true,
      } as never);
      localStorage.setItem(
        "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END",
        storedRagDraft({
          llmModelId: "llama:judge",
          selectedExperimentalPresetCodes: ["P0"],
          benchmarkRuntimeParameters: {
            temperature: 0.9,
            topP: 0.8,
            seed: 1,
            maxTokens: 256,
            topK: 5,
            similarityThreshold: 0.4,
            secondaryLlmModelId: "llama:fast",
          },
        }),
      );
    });

    it("RAG route hides generation parameters, LLM selector, and shows retrieval callout with settings link", async () => {
      renderRagRouteCard();
      expect(await screen.findByTestId("lab-embedding-retrieval-parameters-section")).toBeInTheDocument();
      expect(screen.getByTestId("lab-rag-task-llm-callout")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Open Assistant Configuration/i })).toHaveAttribute("href", "/en/settings/user");
      expect(screen.queryByText("Generation parameters")).not.toBeInTheDocument();
      expect(screen.queryByTestId("lab-hp-temperature")).not.toBeInTheDocument();
      expect(screen.queryByTestId("lab-hyperparameters-form")).not.toBeInTheDocument();
      expect(screen.queryByTestId("lab-generation-parameters-section")).not.toBeInTheDocument();
      expect(screen.queryByTestId("lab-benchmark-llm-model")).not.toBeInTheDocument();
      expect(screen.queryByText("Primary model snapshot / campaign label")).not.toBeInTheDocument();
      expect(screen.queryByText(/campaign label/i)).not.toBeInTheDocument();
      expect(screen.getByTestId("lab-hp-top-k")).toBeInTheDocument();
      expect(screen.getByTestId("lab-hp-similarity-threshold")).toBeInTheDocument();
      expect(screen.getByTestId("lab-benchmark-embedding-model")).toBeInTheDocument();
    });

    it("LLM route still shows generation parameters", async () => {
      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="LLM_JUDGE_QA"
            sectionKey="evaluation-llm"
            taskTypeHint="LLM_EVALUATION"
            cardTitle="LLM evaluation"
            runButtonTestId="lab-llm-run"
            radioGroupName="follow-llm-route"
          />
        </LabEvalHarness>,
      );
      expect(await screen.findByTestId("lab-hyperparameters-form")).toBeInTheDocument();
      expect(screen.getByTestId("lab-hp-temperature")).toBeInTheDocument();
      expect(screen.queryByText("Generation parameters")).not.toBeInTheDocument();
    });

    it("embedding route shows retrieval parameters without generation", async () => {
      vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
        data: [embeddingDataset],
        isLoading: false,
        isFetched: true,
        isSuccess: true,
      } as never);
      localStorage.setItem(
        "lab:evaluation-draft:v1:EMBEDDING_RETRIEVAL",
        storedEmbeddingDraft({ embeddingModelIds: ["mxbai-embed:latest"] }),
      );
      render(
        <LabEvalHarness>
          <LabEvaluationRunCard
            benchmarkKind="EMBEDDING_RETRIEVAL"
            sectionKey="evaluation-embedding"
            taskTypeHint="EMBEDDING_EVALUATION"
            cardTitle="Embedding evaluation"
            runButtonTestId="lab-embedding-run"
            radioGroupName="follow-embedding-route"
          />
        </LabEvalHarness>,
      );
      expect(await screen.findByTestId("lab-hyperparameters-form")).toBeInTheDocument();
      expect(screen.getByTestId("lab-hp-top-k")).toBeInTheDocument();
      expect(screen.queryByTestId("lab-hp-temperature")).not.toBeInTheDocument();
      expect(screen.queryByText("Generation parameters")).not.toBeInTheDocument();
    });

    it("RAG POST omits generation keys and llmModelId from benchmarkRuntimeParameters", async () => {
      const user = userEvent.setup();
      vi.mocked(apiFetch).mockResolvedValue({
        evaluationRunId: "run-0000-0000-0000-000000000099",
        asyncTaskId: "job-0000-0000-0000-000000000099",
      });
      renderRagRouteCard();
      await user.click(await screen.findByTestId("lab-rag-run"));
      const call = vi.mocked(apiFetch).mock.calls.find((c) => {
        const url = String(c[0] ?? "");
        const method = (c[1] as RequestInit | undefined)?.method ?? "GET";
        return url.includes("/lab/benchmarks/RAG_PRESET_END_TO_END/runs") && method === "POST";
      });
      expect(call).toBeTruthy();
      const body = JSON.parse(String((call?.[1] as RequestInit | undefined)?.body ?? "{}")) as {
        llmModelId?: string;
        llmModelIds?: string[];
        embeddingModelId?: string;
        benchmarkRuntimeParameters?: Record<string, unknown>;
      };
      expect(body.llmModelId).toBeUndefined();
      expect(body.llmModelIds).toBeUndefined();
      expect(body.embeddingModelId).toBe("mxbai-embed:latest");
      const params = body.benchmarkRuntimeParameters ?? {};
      expect(params.temperature).toBeUndefined();
      expect(params.top_p).toBeUndefined();
      expect(params.topP).toBeUndefined();
      expect(params.seed).toBeUndefined();
      expect(params.max_tokens).toBeUndefined();
      expect(params.maxTokens).toBeUndefined();
      expect(params.presence_penalty).toBeUndefined();
      expect(params.frequency_penalty).toBeUndefined();
      expect(params.response_format).toBeUndefined();
      expect(params.stop).toBeUndefined();
      expect(params.think).toBeUndefined();
      expect(params.secondaryLlmModelId).toBeUndefined();
      expect(params.topK).toBe(5);
      expect(params.similarityThreshold).toBe(0.4);
    });
  });
});
