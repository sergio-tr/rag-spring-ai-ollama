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

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    selector({ activeProject: null }),
}));

import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";

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

describe("LabEvaluationRunCard", () => {
  beforeEach(() => {
    vi.mocked(useLabStatus).mockReset();
    vi.mocked(useExperimentalDatasetsQuery).mockReset();
    vi.mocked(useExperimentalPresetCatalog).mockReset();
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
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000010",
          code: "P0",
          family: "baseline",
          label: "Direct LLM",
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
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000021",
          code: "P11",
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
    expect(screen.getByText(/P11 — Clarification loop/i)).toBeInTheDocument();
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
});
