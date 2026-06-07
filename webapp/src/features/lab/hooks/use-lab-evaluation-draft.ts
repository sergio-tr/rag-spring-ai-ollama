"use client";

import {
  clearLabEvaluationDraftStorage,
  computeLabEvaluationDraftWarnings,
  defaultLabEvaluationDraft,
  labEvaluationDraftStorageKey,
  loadLabEvaluationDraftWithSanitationReport,
  saveLabEvaluationDraft,
  type LabEvaluationDraftKind,
  type LabEvaluationDraftStored,
  type LabEvaluationDraftWarnings,
} from "@/features/lab/lib/lab-evaluation-draft";
import { sanitizeLabBenchmarkDraftPresetCodes } from "@/features/lab/lib/experimental-preset-selection";
import type { ExperimentalDatasetListItemDto, ExperimentalPresetCatalogItemDto } from "@/types/api";
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
  catalogPresets?: ExperimentalPresetCatalogItemDto[];
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
    corpusId: stored.corpusId,
  };
}

function initialDraftState(kind: LabEvaluationDraftKind): {
  draft: Omit<LabEvaluationDraftStored, "v">;
  sanitizedRemovedPresets: string[];
} {
  const { draft: loaded, removedPresets } = loadLabEvaluationDraftWithSanitationReport(kind);
  return {
    draft: withoutStorageVersion(loaded),
    sanitizedRemovedPresets: removedPresets,
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
  const [initial] = useState(() => initialDraftState(kind));
  const [draft, setDraft] = useState(initial.draft);
  const [sanitizedRemovedPresets, setSanitizedRemovedPresets] = useState(initial.sanitizedRemovedPresets);

  useEffect(() => {
    saveLabEvaluationDraft(kind, draft);
  }, [kind, draft]);

  useEffect(() => {
    if (kind !== "RAG_PRESET_END_TO_END" || !validation.presetsCatalogReady) {
      return;
    }
    const { selected, removed } = sanitizeLabBenchmarkDraftPresetCodes(
      draft.selectedExperimentalPresetCodes,
      validation.catalogPresets,
      true,
    );
    if (removed.length === 0) {
      return;
    }
    queueMicrotask(() => {
      setSanitizedRemovedPresets((prev) => Array.from(new Set([...prev, ...removed])));
      setDraft((prev) => {
        if (
          prev.selectedExperimentalPresetCodes.length === selected.length &&
          prev.selectedExperimentalPresetCodes.every((code, index) => code === selected[index])
        ) {
          return prev;
        }
        return { ...prev, selectedExperimentalPresetCodes: selected };
      });
    });
  }, [
    kind,
    draft.selectedExperimentalPresetCodes,
    validation.catalogPresets,
    validation.presetsCatalogReady,
  ]);

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
    setSanitizedRemovedPresets([]);
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
    sanitizedRemovedPresets,
    storageKey: labEvaluationDraftStorageKey(kind),
  };
}
