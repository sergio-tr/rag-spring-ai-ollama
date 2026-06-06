import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";

vi.mock("@/features/lab/hooks/use-lab-job-live-stream", () => ({
  useLabJobLiveStream: vi.fn(() => ({
    connectionState: null,
    taskStatus: null,
    recentEvents: [],
    progressSnapshot: undefined,
    stop: vi.fn(),
  })),
}));

vi.mock("@/features/lab/hooks/use-auto-resume-lab-jobs", () => ({
  useAutoResumeLabJobs: (opts: {
    onAutoFollow?: (args: {
      candidate: {
        accepted: { jobId: string; status: string; pollPath: string; streamPath: string };
        evaluationRunId: string;
        jobId: string;
      };
      status: { terminal: boolean; status: string; errorMessage: string };
    }) => void | Promise<void>;
  }) => {
    const { useEffect } = require("react") as typeof import("react");
    useEffect(() => {
      void opts.onAutoFollow?.({
        candidate: {
          jobId: "job-failed-m10",
          accepted: {
            jobId: "job-failed-m10",
            status: "FAILED",
            pollPath: "/lab/jobs/job-failed-m10",
            streamPath: "/lab/jobs/job-failed-m10/events",
          },
          evaluationRunId: "fc9ea380-b255-43e2-b203-3b900804ffc9",
        },
        status: {
          terminal: true,
          status: "FAILED",
          errorMessage: "NullPointerException",
        },
      });
    }, [opts]);
    return { decision: { kind: "none" as const }, followCandidate: vi.fn() };
  },
}));

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: vi.fn(() => ({
    data: {
      datasetKindsReady: true,
      datasets: { enabled: true, datasetKindsReady: true },
      evaluations: { llm: true, rag: true, classifierProxy: false, asyncJobs: true },
      classifier: { configured: true, train: true, evaluate: true },
      message: "",
    },
  })),
}));

vi.mock("@/features/lab/hooks/use-evaluation-corpus", () => ({
  useEvaluationCorpus: vi.fn(() => ({
    summary: { documentCount: 1, readyCount: 1, documents: [] },
    effectiveCorpusId: "corpus-1",
    loading: false,
    fetching: false,
    error: null,
    refresh: vi.fn(),
    ensureCorpus: vi.fn(),
    uploadDocuments: vi.fn(),
    corpusReady: true,
    corpusRunnable: true,
    readiness: { runnable: true, primaryBlocker: null, primaryBlockerMessage: null },
    corpusProcessing: false,
    attachFromProject: vi.fn(),
    deleteDocument: vi.fn(),
    deleteAllDocuments: vi.fn(),
    retryDocumentIngest: vi.fn(),
  })),
}));

vi.mock("@/features/lab/hooks/use-experimental-datasets", () => ({
  useExperimentalDatasetsQuery: vi.fn(() => ({
    data: [
      {
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
      },
    ],
    isLoading: false,
    isFetched: true,
    isSuccess: true,
  })),
}));

vi.mock("@/features/lab/hooks/use-experimental-preset-catalog", () => ({
  useExperimentalPresetCatalog: vi.fn(() => ({
    data: [{ code: "P0", label: "P0", supportStatus: "EXECUTABLE", reasonIfUnsupported: null }],
    isLoading: false,
    isSuccess: true,
  })),
}));

vi.mock("@/features/lab/hooks/use-active-lab-jobs", () => ({
  useActiveLabJobs: vi.fn(() => ({ data: [], isLoading: false, isFetched: true, isError: false })),
}));

vi.mock("@/features/chat/hooks/use-models-by-type", () => ({
  useModelsByType: vi.fn(() => ({
    data: [{ modelId: "nomic-embed-test" }],
    isLoading: false,
    isFetched: true,
  })),
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: null }) => unknown) => selector({ activeProject: null }),
}));

vi.mock("@/features/lab/components/lab-benchmark-results-panel", () => ({
  LabBenchmarkResultsPanel: () => <div data-testid="lab-benchmark-results-panel-stub" />,
}));

import { LabEvaluationRunCard } from "./lab-evaluation-run-card";

describe("LabEvaluationRunCard failed job notice", () => {
  beforeEach(() => {
    localStorage.setItem(
      "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END",
      JSON.stringify({
        v: 1,
        datasetId: "ragguid-ragguid-ragguid-ragguid-000001",
        explicitDraftClear: false,
        embeddingModelId: "nomic-embed-test",
        selectedExperimentalPresetCodes: ["P0"],
        corpusId: "corpus-1",
      }),
    );
  });

  it("shows failed job notice when terminal FAILED job is recovered", async () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabEvaluationRunCard
            benchmarkKind="RAG_PRESET_END_TO_END"
            sectionKey="evaluation-rag"
            taskTypeHint="RAG_EVALUATION"
            cardTitle="RAG evaluation"
            runButtonTestId="lab-rag-run"
            radioGroupName="follow-rag-failed"
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("lab-failed-job-results-notice")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("lab-benchmark-results-panel-stub")).not.toBeInTheDocument();
  });
});
