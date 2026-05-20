import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import type { BenchmarkKind, ExperimentalDatasetListItemDto } from "@/types/api";

export type LabEvaluationDraftKind = Extract<
  BenchmarkKind,
  "LLM_JUDGE_QA" | "EMBEDDING_RETRIEVAL" | "RAG_PRESET_END_TO_END"
>;

export const LAB_EVALUATION_DRAFT_STORAGE_PREFIX = "lab:evaluation-draft:v1:";

export function labEvaluationDraftStorageKey(kind: LabEvaluationDraftKind): string {
  return `${LAB_EVALUATION_DRAFT_STORAGE_PREFIX}${kind}`;
}

const V1_FORM_STORAGE_PREFIX = "rag-lab-form-v1:";

export type LabEvaluationDraftStored = {
  v: 1;
  datasetId: string | null;
  /** When true, do not auto-fill the dataset from server defaults (user cleared the form or chose an empty selection). */
  explicitDraftClear: boolean;
  llmModelId: string;
  llmModelIds: string[];
  embeddingModelId: string;
  embeddingModelIds: string[];
  embeddingDownstreamRag: boolean;
  selectedExperimentalPresetCodes: string[];
  runName: string;
  followMode: LabJobFollowMode;
  lastEvaluationRunId: string | null;
};

export function defaultLabEvaluationDraft(): Omit<LabEvaluationDraftStored, "v"> {
  return {
    datasetId: null,
    explicitDraftClear: false,
    llmModelId: "",
    llmModelIds: [],
    embeddingModelId: "",
    embeddingModelIds: [],
    embeddingDownstreamRag: false,
    selectedExperimentalPresetCodes: [],
    runName: "",
    followMode: "poll",
    lastEvaluationRunId: null,
  };
}

function coerceFollowMode(raw: unknown): LabJobFollowMode {
  return raw === "sse" ? "sse" : "poll";
}

/**
 * Migrates v1 flat JSON saved under {@code rag-lab-form-v1:{kind}}.
 */
function migrateV1FormParsed(kind: LabEvaluationDraftKind, parsed: Record<string, unknown>): Omit<LabEvaluationDraftStored, "v"> {
  const base = defaultLabEvaluationDraft();
  const userDatasetId = parsed.userDatasetId;
  if (typeof userDatasetId === "string") base.datasetId = userDatasetId;
  else if (userDatasetId === null) base.datasetId = null;

  if (typeof parsed.llmModelId === "string") base.llmModelId = parsed.llmModelId;
  if (Array.isArray(parsed.llmModelIds) && parsed.llmModelIds.every((x) => typeof x === "string"))
    base.llmModelIds = parsed.llmModelIds as string[];

  if (typeof parsed.embeddingModelId === "string") base.embeddingModelId = parsed.embeddingModelId;
  if (Array.isArray(parsed.embeddingModelIds) && parsed.embeddingModelIds.every((x) => typeof x === "string"))
    base.embeddingModelIds = parsed.embeddingModelIds as string[];

  if (typeof parsed.embeddingDownstreamRag === "boolean") base.embeddingDownstreamRag = parsed.embeddingDownstreamRag;

  if (
    Array.isArray(parsed.selectedExperimentalPresetCodes) &&
    parsed.selectedExperimentalPresetCodes.every((x) => typeof x === "string")
  ) {
    base.selectedExperimentalPresetCodes = parsed.selectedExperimentalPresetCodes as string[];
  }

  base.followMode = coerceFollowMode(parsed.followMode);

  if (typeof parsed.explicitDraftClear === "boolean") base.explicitDraftClear = parsed.explicitDraftClear;

  return base;
}

export function loadLabEvaluationDraft(kind: LabEvaluationDraftKind): LabEvaluationDraftStored {
  const key = labEvaluationDraftStorageKey(kind);
  try {
    const rawNew = localStorage.getItem(key);
    if (rawNew) {
      const parsed = JSON.parse(rawNew) as Record<string, unknown>;
      if (parsed && typeof parsed === "object" && parsed.v === 1) {
        const d = defaultLabEvaluationDraft();
        return {
          v: 1,
          datasetId: typeof parsed.datasetId === "string" ? parsed.datasetId : parsed.datasetId === null ? null : d.datasetId,
          explicitDraftClear:
            typeof parsed.explicitDraftClear === "boolean" ? parsed.explicitDraftClear : d.explicitDraftClear,
          llmModelId: typeof parsed.llmModelId === "string" ? parsed.llmModelId : d.llmModelId,
          llmModelIds:
            Array.isArray(parsed.llmModelIds) && parsed.llmModelIds.every((x) => typeof x === "string")
              ? (parsed.llmModelIds as string[])
              : d.llmModelIds,
          embeddingModelId: typeof parsed.embeddingModelId === "string" ? parsed.embeddingModelId : d.embeddingModelId,
          embeddingModelIds:
            Array.isArray(parsed.embeddingModelIds) && parsed.embeddingModelIds.every((x) => typeof x === "string")
              ? (parsed.embeddingModelIds as string[])
              : d.embeddingModelIds,
          embeddingDownstreamRag:
            typeof parsed.embeddingDownstreamRag === "boolean" ? parsed.embeddingDownstreamRag : d.embeddingDownstreamRag,
          selectedExperimentalPresetCodes:
            Array.isArray(parsed.selectedExperimentalPresetCodes) &&
            parsed.selectedExperimentalPresetCodes.every((x) => typeof x === "string")
              ? (parsed.selectedExperimentalPresetCodes as string[])
              : d.selectedExperimentalPresetCodes,
          runName: typeof parsed.runName === "string" ? parsed.runName : d.runName,
          followMode: coerceFollowMode(parsed.followMode),
          lastEvaluationRunId:
            typeof parsed.lastEvaluationRunId === "string"
              ? parsed.lastEvaluationRunId
              : parsed.lastEvaluationRunId === null
                ? null
                : d.lastEvaluationRunId,
        };
      }
    }

    const v1StorageKey = `${V1_FORM_STORAGE_PREFIX}${kind}`;
    const rawV1 = localStorage.getItem(v1StorageKey);
    if (rawV1) {
      const parsed = JSON.parse(rawV1) as Record<string, unknown>;
      const migrated = migrateV1FormParsed(kind, parsed);
      const stored: LabEvaluationDraftStored = { v: 1, ...migrated };
      localStorage.setItem(key, JSON.stringify(stored));
      localStorage.removeItem(v1StorageKey);
      return stored;
    }
  } catch {
    // corrupted storage — fall through
  }
  return { v: 1, ...defaultLabEvaluationDraft() };
}

export function saveLabEvaluationDraft(kind: LabEvaluationDraftKind, draft: Omit<LabEvaluationDraftStored, "v">): void {
  try {
    localStorage.setItem(labEvaluationDraftStorageKey(kind), JSON.stringify({ v: 1, ...draft }));
  } catch {
    // quota / private mode
  }
}

export function clearLabEvaluationDraftStorage(kind: LabEvaluationDraftKind): void {
  try {
    localStorage.removeItem(labEvaluationDraftStorageKey(kind));
    localStorage.removeItem(`${V1_FORM_STORAGE_PREFIX}${kind}`);
  } catch {
    /* noop */
  }
}

export type LabEvaluationDraftWarnings = {
  datasetDeletedOrUnknown: boolean;
  datasetIncompatibleWithBenchmark: boolean;
  llmModelInvalid: boolean;
  llmModelsInvalid: string[];
  embeddingModelInvalid: boolean;
  embeddingModelsInvalid: string[];
  presetsUnknown: string[];
};

export function computeLabEvaluationDraftWarnings(input: {
  kind: LabEvaluationDraftKind;
  draft: Omit<LabEvaluationDraftStored, "v">;
  compatibleDatasetRows: ExperimentalDatasetListItemDto[];
  allDatasetRows: ExperimentalDatasetListItemDto[];
  datasetsFetched: boolean;
  availableLlmModelIds: string[];
  availableEmbeddingModelIds: string[];
  catalogPresetCodes: string[];
  presetsCatalogReady: boolean;
}): LabEvaluationDraftWarnings {
  const emptyWarnings = (): LabEvaluationDraftWarnings => ({
    datasetDeletedOrUnknown: false,
    datasetIncompatibleWithBenchmark: false,
    llmModelInvalid: false,
    llmModelsInvalid: [],
    embeddingModelInvalid: false,
    embeddingModelsInvalid: [],
    presetsUnknown: [],
  });

  const id = input.draft.datasetId?.trim();
  if (!id) return emptyWarnings();

  const allKnownIds = new Set(input.allDatasetRows.map((r) => r.id));
  const compatibleIds = new Set(input.compatibleDatasetRows.map((r) => r.id));

  let datasetDeletedOrUnknown = false;
  let datasetIncompatibleWithBenchmark = false;

  if (input.datasetsFetched && !allKnownIds.has(id)) {
    datasetDeletedOrUnknown = true;
  } else if (input.datasetsFetched && allKnownIds.has(id) && !compatibleIds.has(id)) {
    datasetIncompatibleWithBenchmark = true;
  }

  const llmSet = new Set(input.availableLlmModelIds);
  const embSet = new Set(input.availableEmbeddingModelIds);

  const llmModelInvalid =
    input.draft.llmModelId.trim() !== "" && input.kind !== "EMBEDDING_RETRIEVAL" ? !llmSet.has(input.draft.llmModelId.trim()) : false;

  const llmModelsInvalid =
    input.kind === "LLM_JUDGE_QA"
      ? input.draft.llmModelIds.filter((m) => m.trim() !== "" && !llmSet.has(m.trim()))
      : [];

  const embeddingNeedsValidation =
    input.kind === "EMBEDDING_RETRIEVAL" || input.kind === "RAG_PRESET_END_TO_END";
  const embeddingModelInvalid =
    embeddingNeedsValidation && input.draft.embeddingModelId.trim() !== ""
      ? !embSet.has(input.draft.embeddingModelId.trim())
      : false;
  const embeddingModelsInvalid =
    input.kind === "EMBEDDING_RETRIEVAL"
      ? input.draft.embeddingModelIds.filter((m) => m.trim() !== "" && !embSet.has(m.trim()))
      : [];

  const presetSet = new Set(input.catalogPresetCodes);
  const presetsUnknown =
    input.kind === "RAG_PRESET_END_TO_END" && input.presetsCatalogReady
      ? input.draft.selectedExperimentalPresetCodes.filter((c) => !presetSet.has(c))
      : [];

  return {
    datasetDeletedOrUnknown,
    datasetIncompatibleWithBenchmark,
    llmModelInvalid,
    llmModelsInvalid,
    embeddingModelInvalid,
    embeddingModelsInvalid,
    presetsUnknown,
  };
}
