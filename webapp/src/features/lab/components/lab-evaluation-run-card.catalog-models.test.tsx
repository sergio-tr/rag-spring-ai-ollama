import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { LabEvaluationRunCard } from "./lab-evaluation-run-card";
import { useLabEvaluationModels } from "@/features/lab/hooks/use-lab-evaluation-models";
import type { LabEvaluationModelDto } from "@/types/api";

const LEGACY_PREFERRED = ["gemma3:4b", "mistral:7b", "llama3.1:8b"] as const;

const chatModels: LabEvaluationModelDto[] = [
  {
    modelName: "gpt-oss:20b",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "UNKNOWN",
    embeddingDimensions: null,
    compatibleWithCurrentVectorStore: null,
    usableAsDefault: true,
  },
];

const embeddingModels: LabEvaluationModelDto[] = [
  {
    modelName: "hf-embed:latest",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "UNKNOWN",
    embeddingDimensions: 1024,
    compatibleWithCurrentVectorStore: true,
    usableAsDefault: true,
  },
  {
    modelName: "wrong-dim:latest",
    evalSelectable: false,
    blockedReason: "Incompatible with vector store",
    blockedReasonCode: "EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE",
    runtimeStatus: "UNKNOWN",
    embeddingDimensions: 768,
    compatibleWithCurrentVectorStore: false,
    usableAsDefault: false,
  },
];

vi.mock("@/features/lab/hooks/use-evaluation-corpus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/lab/hooks/use-evaluation-corpus")>();
  return {
    ...actual,
    useEvaluationCorpus: () => ({
      summary: { documentCount: 0, readyCount: 0, documents: [] },
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
    }),
  };
});

vi.mock("@/features/help/HelpPopover", () => ({ HelpPopover: () => null }));
vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: () => ({ data: { datasetKindsReady: true, datasets: { enabled: true } } }),
}));
vi.mock("@/features/lab/hooks/use-experimental-datasets", () => ({
  useExperimentalDatasetsQuery: () => ({
    data: [
      {
        id: "550e8400-e29b-41d4-a716-446655440000",
        name: "ds",
        experimentalDatasetType: "LLM_MODEL_BASELINE",
        validationStatus: "VALID",
        questionCounts: { llmReaderQuestions: 2, embeddingQueries: 0, ragPresetQuestions: 0, presetCatalog: 0, chunkRegistry: 0 },
        canRunLlmBaseline: true,
        canRunEmbeddingBaseline: false,
        canRunRagPresetBenchmark: false,
        isDemoDataset: false,
      },
    ],
    isLoading: false,
    isFetched: true,
    isSuccess: true,
  }),
}));
vi.mock("@/features/lab/hooks/use-experimental-preset-catalog", () => ({
  useExperimentalPresetCatalog: () => ({ data: [], isSuccess: true }),
}));
vi.mock("@/features/lab/hooks/use-active-lab-jobs", () => ({
  useActiveLabJobs: () => ({ data: [], isLoading: false }),
}));
vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: null }) => unknown) => selector({ activeProject: null }),
}));
vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...actual, apiFetch: vi.fn(), apiProductPath: (p: string) => p };
});

vi.mock("@/features/lab/hooks/use-lab-evaluation-models", () => ({
  useLabEvaluationModels: vi.fn(),
}));

function mockCatalog(
  chat: LabEvaluationModelDto[] = chatModels,
  embedding: LabEvaluationModelDto[] = embeddingModels,
) {
  vi.mocked(useLabEvaluationModels).mockImplementation(((capability: string) => ({
    data:
      capability === "CHAT"
        ? {
            effectiveProvider: "OPENAI_COMPATIBLE",
            capability: "CHAT",
            models: chat,
            hasCompatibleEmbeddingModels: embedding.some((m) => m.compatibleWithCurrentVectorStore === true),
          }
        : {
            effectiveProvider: "OPENAI_COMPATIBLE",
            capability: "EMBEDDING",
            models: embedding,
            hasCompatibleEmbeddingModels: embedding.some((m) => m.compatibleWithCurrentVectorStore === true),
          },
    isLoading: false,
    isSuccess: true,
    isError: false,
  })) as never);
}

function renderLlmCard() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <IntlTestProvider locale="en">
        <LabEvaluationRunCard
          benchmarkKind="LLM_JUDGE_QA"
          sectionKey="evaluation-llm-catalog"
          taskTypeHint="LLM_EVALUATION"
          cardTitle="LLM evaluation"
          runButtonTestId="lab-llm-catalog-run"
          radioGroupName="follow-catalog"
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

function renderRagCard() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <IntlTestProvider locale="en">
        <LabEvaluationRunCard
          benchmarkKind="RAG_PRESET_END_TO_END"
          sectionKey="evaluation-rag-catalog"
          taskTypeHint="RAG_EVALUATION"
          cardTitle="RAG evaluation"
          runButtonTestId="lab-rag-catalog-run"
          radioGroupName="follow-rag-catalog"
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("LabEvaluationRunCard catalog models", () => {
  beforeEach(() => {
    mockCatalog();
  });

  it("evaluation selector renders catalog models", async () => {
    renderLlmCard();
    expect(await screen.findByTestId("lab-benchmark-llm-models-gpt-oss:20b")).toBeInTheDocument();
  });

  it("old preferred model list is not used", async () => {
    renderLlmCard();
    await screen.findByTestId("lab-benchmark-llm-models-gpt-oss:20b");
    const group = screen.getByTestId("lab-benchmark-llm-models-list");
    const text = group.textContent ?? "";
    for (const legacy of LEGACY_PREFERRED) {
      expect(text).not.toContain(legacy);
    }
  });

  it("incompatible embeddings are disabled", async () => {
    mockCatalog(chatModels, embeddingModels);
    renderRagCard();
    const option = await screen.findByTestId("lab-benchmark-embedding-option-wrong-dim:latest");
    expect(option).toBeDisabled();
    expect(option.textContent).toMatch(/Incompatible with vector store/i);
  });

  it("no model configured shows blocking error", async () => {
    mockCatalog([], []);
    renderLlmCard();
    expect(await screen.findByTestId("lab-eval-catalog-block")).toHaveTextContent(
      /No chat models are configured/i,
    );
  });
});
