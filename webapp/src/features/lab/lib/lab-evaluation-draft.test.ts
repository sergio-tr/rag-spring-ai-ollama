import { describe, expect, it, beforeEach } from "vitest";
import type { ExperimentalDatasetListItemDto } from "@/types/api";
import {
  LAB_EVALUATION_DRAFT_STORAGE_PREFIX,
  clearLabEvaluationDraftStorage,
  computeLabEvaluationDraftWarnings,
  defaultLabEvaluationDraft,
  labEvaluationDraftStorageKey,
  loadLabEvaluationDraft,
  saveLabEvaluationDraft,
} from "./lab-evaluation-draft";

describe("lab-evaluation-draft", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("uses versioned storage keys per benchmark kind", () => {
    expect(labEvaluationDraftStorageKey("LLM_JUDGE_QA")).toBe(`${LAB_EVALUATION_DRAFT_STORAGE_PREFIX}LLM_JUDGE_QA`);
  });

  it("stores dataset selection on reload simulation via save/load round-trip", () => {
    const draft = {
      ...defaultLabEvaluationDraft(),
      datasetId: "ds-main",
      runName: "Round-trip bench",
    };
    saveLabEvaluationDraft("LLM_JUDGE_QA", draft);
    expect(loadLabEvaluationDraft("LLM_JUDGE_QA").datasetId).toBe("ds-main");
    expect(loadLabEvaluationDraft("LLM_JUDGE_QA").runName).toBe("Round-trip bench");
  });

  it("loads defaults when stored JSON is corrupted", () => {
    localStorage.setItem(labEvaluationDraftStorageKey("LLM_JUDGE_QA"), "{not-json");
    const d = loadLabEvaluationDraft("LLM_JUDGE_QA");
    expect(d.v).toBe(1);
    expect(d.datasetId).toBeNull();
    expect(d.followMode).toBe("poll");
  });

  it("migrates rag-lab-form-v1-v1 storage and removes v1 storage key", () => {
    const v1StorageKey = "rag-lab-form-v1:LLM_JUDGE_QA";
    localStorage.setItem(
      v1StorageKey,
      JSON.stringify({
        userDatasetId: "v1-ds",
        llmModelId: "llm-1",
        llmModelIds: ["llm-a", "llm-b"],
        embeddingModelId: "emb-1",
        embeddingModelIds: ["emb-a"],
        embeddingDownstreamRag: true,
        selectedExperimentalPresetCodes: ["P0"],
        followMode: "sse",
        explicitDraftClear: true,
      }),
    );
    const d = loadLabEvaluationDraft("LLM_JUDGE_QA");
    expect(d.datasetId).toBe("v1-ds");
    expect(d.llmModelId).toBe("llm-1");
    expect(d.llmModelIds).toEqual(["llm-a", "llm-b"]);
    expect(d.embeddingModelId).toBe("emb-1");
    expect(d.embeddingModelIds).toEqual(["emb-a"]);
    expect(d.embeddingDownstreamRag).toBe(true);
    expect(d.selectedExperimentalPresetCodes).toEqual(["P0"]);
    expect(d.followMode).toBe("sse");
    expect(d.explicitDraftClear).toBe(true);
    expect(localStorage.getItem(v1StorageKey)).toBeNull();
    // migrated key should exist
    expect(localStorage.getItem(labEvaluationDraftStorageKey("LLM_JUDGE_QA"))).toContain("\"v\":1");
  });

  it("coerces unknown followMode values to poll on load", () => {
    localStorage.setItem(
      labEvaluationDraftStorageKey("LLM_JUDGE_QA"),
      JSON.stringify({ v: 1, ...defaultLabEvaluationDraft(), followMode: "wat" }),
    );
    expect(loadLabEvaluationDraft("LLM_JUDGE_QA").followMode).toBe("poll");
  });

  it("flags presets unknown vs catalog", () => {
    const draft = {
      ...defaultLabEvaluationDraft(),
      datasetId: "d",
      selectedExperimentalPresetCodes: ["P0", "PX_MISSING"],
    };
    const w = computeLabEvaluationDraftWarnings({
      kind: "RAG_PRESET_END_TO_END",
      draft,
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: ["P0", "P1"],
      presetsCatalogReady: true,
    });
    expect(w.presetsUnknown).toEqual(["PX_MISSING"]);
  });

  it("does not evaluate dataset warnings until datasets are fetched", () => {
    const draft = { ...defaultLabEvaluationDraft(), datasetId: "missing" };
    const w = computeLabEvaluationDraftWarnings({
      kind: "LLM_JUDGE_QA",
      draft,
      compatibleDatasetRows: [],
      allDatasetRows: [],
      datasetsFetched: false,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(w.datasetDeletedOrUnknown).toBe(false);
    expect(w.datasetIncompatibleWithBenchmark).toBe(false);
  });

  it("flags dataset as deleted/unknown when fetched but not present in all datasets", () => {
    const draft = { ...defaultLabEvaluationDraft(), datasetId: "d-missing" };
    const w = computeLabEvaluationDraftWarnings({
      kind: "LLM_JUDGE_QA",
      draft,
      compatibleDatasetRows: [],
      allDatasetRows: [{ id: "d-other" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(w.datasetDeletedOrUnknown).toBe(true);
    expect(w.datasetIncompatibleWithBenchmark).toBe(false);
  });

  it("flags dataset as incompatible when present but not in compatible list", () => {
    const draft = { ...defaultLabEvaluationDraft(), datasetId: "d1" };
    const w = computeLabEvaluationDraftWarnings({
      kind: "LLM_JUDGE_QA",
      draft,
      compatibleDatasetRows: [{ id: "d2" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d1" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(w.datasetDeletedOrUnknown).toBe(false);
    expect(w.datasetIncompatibleWithBenchmark).toBe(true);
  });

  it("validates llmModelId for non-embedding benchmark kinds only", () => {
    const base = defaultLabEvaluationDraft();
    const wJudge = computeLabEvaluationDraftWarnings({
      kind: "LLM_JUDGE_QA",
      draft: { ...base, datasetId: "d", llmModelId: "missing-llm" },
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: ["ok-llm"],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(wJudge.llmModelInvalid).toBe(true);

    const wEmbedding = computeLabEvaluationDraftWarnings({
      kind: "EMBEDDING_RETRIEVAL",
      draft: { ...base, datasetId: "d", llmModelId: "missing-llm" },
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: ["ok-llm"],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(wEmbedding.llmModelInvalid).toBe(false);
  });

  it("validates llmModelIds array only for LLM_JUDGE_QA", () => {
    const base = defaultLabEvaluationDraft();
    const w = computeLabEvaluationDraftWarnings({
      kind: "LLM_JUDGE_QA",
      draft: { ...base, datasetId: "d", llmModelIds: ["ok", "missing", ""] },
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: ["ok"],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(w.llmModelsInvalid).toEqual(["missing"]);

    const wOther = computeLabEvaluationDraftWarnings({
      kind: "RAG_PRESET_END_TO_END",
      draft: { ...base, datasetId: "d", llmModelIds: ["missing"] },
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: ["ok"],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(wOther.llmModelsInvalid).toEqual([]);
  });

  it("validates embedding model ids only when benchmark needs an embedding model", () => {
    const base = defaultLabEvaluationDraft();
    const wRag = computeLabEvaluationDraftWarnings({
      kind: "RAG_PRESET_END_TO_END",
      draft: { ...base, datasetId: "d", embeddingModelId: "missing-emb" },
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: ["ok-emb"],
      catalogPresetCodes: ["P0"],
      presetsCatalogReady: true,
    });
    expect(wRag.embeddingModelInvalid).toBe(true);

    const wJudge = computeLabEvaluationDraftWarnings({
      kind: "LLM_JUDGE_QA",
      draft: { ...base, datasetId: "d", embeddingModelId: "missing-emb" },
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: ["ok-emb"],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(wJudge.embeddingModelInvalid).toBe(false);
  });

  it("does not flag unknown presets until catalog is ready", () => {
    const draft = {
      ...defaultLabEvaluationDraft(),
      datasetId: "d",
      selectedExperimentalPresetCodes: ["PX_MISSING"],
    };
    const w = computeLabEvaluationDraftWarnings({
      kind: "RAG_PRESET_END_TO_END",
      draft,
      compatibleDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      allDatasetRows: [{ id: "d" } as ExperimentalDatasetListItemDto],
      datasetsFetched: true,
      availableLlmModelIds: [],
      availableEmbeddingModelIds: [],
      catalogPresetCodes: [],
      presetsCatalogReady: false,
    });
    expect(w.presetsUnknown).toEqual([]);
  });

  it("clearLabEvaluationDraftStorage removes persisted drafts", () => {
    saveLabEvaluationDraft("LLM_JUDGE_QA", { ...defaultLabEvaluationDraft(), datasetId: "x" });
    clearLabEvaluationDraftStorage("LLM_JUDGE_QA");
    expect(loadLabEvaluationDraft("LLM_JUDGE_QA").datasetId).toBeNull();
  });
});
