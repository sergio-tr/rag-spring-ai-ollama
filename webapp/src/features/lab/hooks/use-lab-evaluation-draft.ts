"use client";

import {
  clearLabEvaluationDraftStorage,
  computeLabEvaluationDraftWarnings,
  defaultLabEvaluationDraft,
  labEvaluationDraftStorageKey,
  loadLabEvaluationDraft,
  saveLabEvaluationDraft,
  type LabEvaluationDraftKind,
  type LabEvaluationDraftStored,
  type LabEvaluationDraftWarnings,
} from "@/features/lab/lib/lab-evaluation-draft";
import type { ExperimentalDatasetListItemDto } from "@/types/api";
import { useCallback, useEffect, useMemo, useState } from "react";

export type LabEvaluationDraftPatch =
  | Partial<Omit<LabEvaluationDraftStored, "v">>
  | ((prev: Omit<LabEvaluationDraftStored, "v">) => Partial<Omit<LabEvaluationDraftStored, "v">>);

export type UseLabEvaluationDraftValidationInput = {
  compatibleDatasetRows: ExperimentalDatasetListItemDto[];
  allDatasetRows: ExperimentalDatasetListItemDto[];
  datasetsFetched: boolean;
  availableLlmModelIds: string[];
  availableEmbeddingModelIds: string[];
  catalogPresetCodes: string[];
  presetsCatalogReady: boolean;
};

function withoutStorageVersion(stored: LabEvaluationDraftStored): Omit<LabEvaluationDraftStored, "v"> {
  return {
    datasetId: stored.datasetId,
    explicitDraftClear: stored.explicitDraftClear,
    llmModelId: stored.llmModelId,
    llmModelIds: stored.llmModelIds,
    embeddingModelId: stored.embeddingModelId,
    embeddingModelIds: stored.embeddingModelIds,
    embeddingDownstreamRag: stored.embeddingDownstreamRag,
    selectedExperimentalPresetCodes: stored.selectedExperimentalPresetCodes,
    runName: stored.runName,
    followMode: stored.followMode,
    lastEvaluationRunId: stored.lastEvaluationRunId,
  };
}

/**
 * Persists Lab evaluation form drafts per benchmark kind in {@code localStorage}
 * under {@code lab:evaluation-draft:v1:{kind}}.
 */
export function useLabEvaluationDraft(
  kind: LabEvaluationDraftKind,
  validation: UseLabEvaluationDraftValidationInput,
) {
  const [draft, setDraft] = useState<Omit<LabEvaluationDraftStored, "v">>(() => {
    return withoutStorageVersion(loadLabEvaluationDraft(kind));
  });

  useEffect(() => {
    saveLabEvaluationDraft(kind, draft);
  }, [kind, draft]);

  const warnings: LabEvaluationDraftWarnings = useMemo(
    () =>
      computeLabEvaluationDraftWarnings({
        kind,
        draft,
        compatibleDatasetRows: validation.compatibleDatasetRows,
        allDatasetRows: validation.allDatasetRows,
        datasetsFetched: validation.datasetsFetched,
        availableLlmModelIds: validation.availableLlmModelIds,
        availableEmbeddingModelIds: validation.availableEmbeddingModelIds,
        catalogPresetCodes: validation.catalogPresetCodes,
        presetsCatalogReady: validation.presetsCatalogReady,
      }),
    [
      kind,
      draft,
      validation.compatibleDatasetRows,
      validation.allDatasetRows,
      validation.datasetsFetched,
      validation.availableLlmModelIds,
      validation.availableEmbeddingModelIds,
      validation.catalogPresetCodes,
      validation.presetsCatalogReady,
    ],
  );

  const patchDraft = useCallback((patch: LabEvaluationDraftPatch) => {
    setDraft((prev) => ({ ...prev, ...(typeof patch === "function" ? patch(prev) : patch) }));
  }, []);

  const clearDraft = useCallback(() => {
    clearLabEvaluationDraftStorage(kind);
    const next = { ...defaultLabEvaluationDraft(), explicitDraftClear: true };
    setDraft(next);
  }, [kind]);

  const resetToRecommended = useCallback(
    (recommended: Partial<Omit<LabEvaluationDraftStored, "v">>) => {
      const base = defaultLabEvaluationDraft();
      const merged = { ...base, explicitDraftClear: false, ...recommended };
      setDraft(merged);
    },
    [],
  );

  const setLastEvaluationRunId = useCallback((evaluationRunId: string | null) => {
    setDraft((prev) => ({ ...prev, lastEvaluationRunId: evaluationRunId }));
  }, []);

  return {
    draft,
    patchDraft,
    clearDraft,
    resetToRecommended,
    setLastEvaluationRunId,
    warnings,
    storageKey: labEvaluationDraftStorageKey(kind),
  };
}
