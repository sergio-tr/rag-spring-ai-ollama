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

  it("clearLabEvaluationDraftStorage removes persisted drafts", () => {
    saveLabEvaluationDraft("LLM_JUDGE_QA", { ...defaultLabEvaluationDraft(), datasetId: "x" });
    clearLabEvaluationDraftStorage("LLM_JUDGE_QA");
    expect(loadLabEvaluationDraft("LLM_JUDGE_QA").datasetId).toBeNull();
  });
});
