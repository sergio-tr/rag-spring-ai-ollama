import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useLabEvaluationDraft, type UseLabEvaluationDraftValidationInput } from "./use-lab-evaluation-draft";
import { labEvaluationDraftStorageKey } from "@/features/lab/lib/lab-evaluation-draft";
import type { ExperimentalDatasetListItemDto } from "@/types/api";

const datasetRow = (id: string): ExperimentalDatasetListItemDto =>
  ({
    id,
    name: "Dataset",
    experimentalDatasetType: "RAG_PRESET_BENCHMARK",
    datasetType: "RAG_PRESET",
    readOnly: false,
    validationStatus: "VALID",
    questionCounts: {
      llmReaderQuestions: 0,
      embeddingQueries: 0,
      ragPresetQuestions: 1,
      presetCatalog: 15,
      chunkRegistry: 1,
    },
    isReferenceBundle: false,
    isDemoDataset: false,
    canRunLlmBaseline: false,
    canRunEmbeddingBaseline: false,
    canRunRagPresetBenchmark: true,
    validationIssues: [],
    uploadedAt: "2026-01-01T00:00:00Z",
    description: null,
  }) satisfies ExperimentalDatasetListItemDto;

function validation(overrides: Partial<UseLabEvaluationDraftValidationInput> = {}): UseLabEvaluationDraftValidationInput {
  return {
    compatibleDatasetRows: [],
    allDatasetRows: [],
    datasetsFetched: true,
    availableLlmModelIds: ["llama3.1"],
    availableEmbeddingModelIds: ["nomic-embed-text"],
    catalogPresetCodes: ["P0", "P1"],
    presetsCatalogReady: true,
    ...overrides,
  };
}

describe("useLabEvaluationDraft", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("loads persisted LAB model and preset selections for the benchmark kind", () => {
    localStorage.setItem(
      labEvaluationDraftStorageKey("RAG_PRESET_END_TO_END"),
      JSON.stringify({
        v: 1,
        datasetId: "ds-1",
        explicitDraftClear: false,
        llmModelId: "llama3.1",
        llmModelIds: [],
        embeddingModelId: "nomic-embed-text",
        embeddingModelIds: [],
        embeddingDownstreamRag: false,
        selectedExperimentalPresetCodes: ["P0", "P1"],
        runName: "benchmark run",
        followMode: "sse",
        lastEvaluationRunId: "run-1",
      }),
    );

    const { result } = renderHook(() =>
      useLabEvaluationDraft(
        "RAG_PRESET_END_TO_END",
        validation({
          compatibleDatasetRows: [datasetRow("ds-1")],
          allDatasetRows: [datasetRow("ds-1")],
        }),
      ),
    );

    expect(result.current.draft.datasetId).toBe("ds-1");
    expect(result.current.draft.embeddingModelId).toBe("nomic-embed-text");
    expect(result.current.draft.selectedExperimentalPresetCodes).toEqual(["P0", "P1"]);
    expect(result.current.draft.followMode).toBe("sse");
    expect(result.current.warnings.presetsUnknown).toEqual([]);
  });

  it("patches, persists, clears, and resets draft state without hiding validation warnings", async () => {
    const { result } = renderHook(() =>
      useLabEvaluationDraft(
        "RAG_PRESET_END_TO_END",
        validation({
          compatibleDatasetRows: [],
          allDatasetRows: [datasetRow("ds-known")],
          availableEmbeddingModelIds: ["mxbai-embed-large", "nomic-embed-text"],
          catalogPresetCodes: ["P0"],
        }),
      ),
    );

    act(() => {
      result.current.patchDraft({
        datasetId: "ds-known",
        embeddingModelId: "missing-embed",
        selectedExperimentalPresetCodes: ["P0", "P99"],
      });
    });

    await waitFor(() => {
      const saved = JSON.parse(localStorage.getItem(result.current.storageKey) ?? "{}") as Record<string, unknown>;
      expect(saved.datasetId).toBe("ds-known");
      expect(saved.embeddingModelId).not.toBe("missing-embed");
    });
    expect(result.current.warnings.datasetIncompatibleWithBenchmark).toBe(true);
    expect(result.current.warnings.embeddingModelInvalid).toBe(false);
    expect(result.current.warnings.presetsUnknown).toEqual(["P99"]);

    act(() => {
      result.current.setLastEvaluationRunId("run-99");
    });
    expect(result.current.draft.lastEvaluationRunId).toBe("run-99");

    act(() => {
      result.current.clearDraft();
    });
    expect(result.current.draft.explicitDraftClear).toBe(true);
    expect(result.current.draft.datasetId).toBeNull();

    act(() => {
      result.current.resetToRecommended({
        datasetId: "ds-known",
        embeddingModelId: "nomic-embed-text",
        selectedExperimentalPresetCodes: ["P0"],
      });
    });
    expect(result.current.draft.explicitDraftClear).toBe(false);
    expect(result.current.draft.embeddingModelId).toBe("nomic-embed-text");
    expect(result.current.warnings.embeddingModelInvalid).toBe(false);
  });
});
