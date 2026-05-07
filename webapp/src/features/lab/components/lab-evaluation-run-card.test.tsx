import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
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

import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { useModelsByType } from "@/features/chat/hooks/use-models-by-type";

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
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"],
    chatSelectable: true,
    labSelectable: true,
  }));
}

describe("LabEvaluationRunCard", () => {
  beforeEach(() => {
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
          code: "PX_LEGACY",
          family: "conversational",
          label: "Clarification loop",
          description: "",
          requiredCapabilities: [],
          supported: false,
          supportStatus: "REQUIRES_MULTI_TURN",
          reasonIfUnsupported: "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED",
          requiresMultiTurn: true,
          mapsToRuntimeCapabilities: {},
          allowedOutcomes: ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"],
          chatSelectable: false,
          labSelectable: true,
        },
      ],
      isSuccess: true,
    } as never);
    vi.mocked(useActiveLabJobs).mockReturnValue({ data: [], isLoading: false } as never);
    vi.mocked(useModelsByType).mockImplementation(((type: string) => ({
      data:
        type === "LLM"
          ? [{ modelId: "llama:judge", displayName: "llama:judge", type: "LLM" }]
          : [{ modelId: "nomic-embed-test", displayName: "nomic-embed-test", type: "EMBEDDING" }],
      isLoading: false,
      isSuccess: true,
    })) as never);
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

  it("keeps canonical benchmark transport hint inside advanced disclosure by default", async () => {
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
    const advancedDetails = screen.getByText(/Advanced options/i).closest("details");
    expect(advancedDetails).not.toHaveAttribute("open");
    expect(advancedDetails).toHaveTextContent(/\/lab\/benchmarks/i);
    await user.click(screen.getByText(/Advanced options/i));
    expect(advancedDetails).toHaveAttribute("open");
  });

  it("shows experimental preset catalog with unsupported reason in RAG benchmark mode", () => {
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
    expect(screen.getByText(/PX_LEGACY — Clarification loop/i)).toBeInTheDocument();
    expect(screen.getByText(/PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED/i)).toBeInTheDocument();
    expect(screen.getByTestId("lab-experimental-presets-select-core")).toBeInTheDocument();
  });

  it("blocks running when dataset is marked as demo for TFG", () => {
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
    expect(screen.getByTestId("lab-benchmark-llm-models-multi")).toHaveValue(["llama:judge"]);

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
    expect(screen.getByTestId("lab-benchmark-llm-models-multi")).toHaveValue(["llama:judge"]);
  });

  it("restores RAG preset picks P0–P14 from localStorage", () => {
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
    for (let i = 0; i <= 14; i += 1) {
      expect(screen.getByTestId(`lab-experimental-preset-P${i}`)).toBeChecked();
    }
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
});
