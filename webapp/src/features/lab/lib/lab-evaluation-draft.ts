import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import type { BenchmarkKind, ExperimentalDatasetListItemDto } from "@/types/api";
import { sanitizeLabBenchmarkDraftPresetCodes } from "@/features/lab/lib/experimental-preset-selection";
import {
  THESIS_DEFAULT_EMBEDDING_MODEL_ID,
  THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID,
  THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID,
} from "@/features/lab/lib/lab-evaluation-models";

export type LabEvaluationDraftKind = Extract<
  BenchmarkKind,
  "LLM_JUDGE_QA" | "EMBEDDING_RETRIEVAL" | "RAG_PRESET_END_TO_END"
>;

export const LAB_EVALUATION_DRAFT_STORAGE_PREFIX = "lab:evaluation-draft:v1:";

export function labEvaluationDraftStorageKey(kind: LabEvaluationDraftKind): string {
  return `${LAB_EVALUATION_DRAFT_STORAGE_PREFIX}${kind}`;
}

const V1_FORM_STORAGE_PREFIX = "rag-lab-form-v1:";

/** Legacy constant removed — embedding defaults come from catalog API (`usableAsDefault`). */
export const LAB_DEFAULT_EMBEDDING_MODEL_ID = "";

import { parseEmbeddingBenchmarkRuntimeParameters } from "@/features/lab/lib/lab-embedding-hyperparameters";

export type LabResponseFormat = "text" | "json_object" | "json_schema";

export type LabBenchmarkRuntimeParameters = {
  temperature?: number;
  topP?: number;
  seed?: number;
  maxTokens?: number;
  presencePenalty?: number;
  frequencyPenalty?: number;
  responseFormat?: LabResponseFormat;
  stop?: string[];
  think?: boolean;
  topK?: number;
  similarityThreshold?: number;
  secondaryLlmModelId?: string;
};

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
  /** Lab evaluation corpus for RAG/embedding document-backed runs. */
  corpusId: string | null;
  /** When true, Lab may rebuild indexes for the selected embedding model (RAG / embedding runs). */
  autoReindex: boolean;
  /** When true, prefer reusing a compatible active snapshot instead of rebuilding. */
  reuseCompatibleActiveSnapshot: boolean;
  benchmarkRuntimeParameters: LabBenchmarkRuntimeParameters;
};

/** Legacy Ollama-only defaults removed from product catalog — cleared on load without warnings. */
export const LEGACY_STALE_LLM_MODEL_IDS = new Set(["gemma3:4b", "mistral:7b", "llama3.1:8b"]);
export const LEGACY_STALE_EMBEDDING_MODEL_IDS = new Set(["mxbai-embed-large:latest", "mxbai-embed-large"]);

export function isLegacyStaleLabLlmModelId(modelId: string): boolean {
  return LEGACY_STALE_LLM_MODEL_IDS.has(modelId.trim());
}

export function isLegacyStaleLabEmbeddingModelId(modelId: string): boolean {
  return LEGACY_STALE_EMBEDDING_MODEL_IDS.has(modelId.trim());
}

export function stripLegacyStaleLabModelIds(
  draft: Omit<LabEvaluationDraftStored, "v">,
): Omit<LabEvaluationDraftStored, "v"> {
  const next = { ...draft };
  if (isLegacyStaleLabLlmModelId(next.llmModelId)) next.llmModelId = "";
  next.llmModelIds = next.llmModelIds.filter((id) => !isLegacyStaleLabLlmModelId(id));
  if (isLegacyStaleLabEmbeddingModelId(next.embeddingModelId)) next.embeddingModelId = "";
  next.embeddingModelIds = next.embeddingModelIds.filter((id) => !isLegacyStaleLabEmbeddingModelId(id));
  return next;
}

function filterModelIdsToCatalog(ids: string[], catalogIds: string[]): string[] {
  if (catalogIds.length === 0) return ids;
  const catalog = new Set(catalogIds.map((id) => id.trim()).filter(Boolean));
  return ids.map((id) => id.trim()).filter((id) => id && catalog.has(id));
}

function draftModelFieldsChanged(
  before: Omit<LabEvaluationDraftStored, "v">,
  after: Omit<LabEvaluationDraftStored, "v">,
): boolean {
  return (
    before.llmModelId !== after.llmModelId ||
    before.embeddingModelId !== after.embeddingModelId ||
    before.llmModelIds.join("|") !== after.llmModelIds.join("|") ||
    before.embeddingModelIds.join("|") !== after.embeddingModelIds.join("|")
  );
}

/** Strips legacy and catalog-invalid model ids; fills empty selections from catalog when available. */
export function migrateLabDraftModelsFromCatalog(
  draft: Omit<LabEvaluationDraftStored, "v">,
  availableLlmModelIds: string[],
  availableEmbeddingModelIds: string[],
  kind?: LabEvaluationDraftKind,
): Omit<LabEvaluationDraftStored, "v"> {
  let next = stripLegacyStaleLabModelIds(draft);

  if (availableLlmModelIds.length > 0) {
    const llmSet = new Set(availableLlmModelIds.map((id) => id.trim()).filter(Boolean));
    if (next.llmModelId.trim() && !llmSet.has(next.llmModelId.trim())) {
      next = { ...next, llmModelId: "" };
    }
    const filteredLlmIds = filterModelIdsToCatalog(next.llmModelIds, availableLlmModelIds);
    if (filteredLlmIds.length !== next.llmModelIds.length) {
      next = { ...next, llmModelIds: filteredLlmIds };
    }
    const secondary = next.benchmarkRuntimeParameters.secondaryLlmModelId?.trim() ?? "";
    if (secondary && !llmSet.has(secondary)) {
      next = {
        ...next,
        benchmarkRuntimeParameters: {
          ...next.benchmarkRuntimeParameters,
          secondaryLlmModelId: undefined,
        },
      };
    }
  }

  if (availableEmbeddingModelIds.length > 0) {
    const embSet = new Set(availableEmbeddingModelIds.map((id) => id.trim()).filter(Boolean));
    if (next.embeddingModelId.trim() && !embSet.has(next.embeddingModelId.trim())) {
      next = { ...next, embeddingModelId: "" };
    }
    const filteredEmbIds = filterModelIdsToCatalog(next.embeddingModelIds, availableEmbeddingModelIds);
    if (filteredEmbIds.length !== next.embeddingModelIds.length) {
      next = { ...next, embeddingModelIds: filteredEmbIds };
    }
  }

  const firstLlm = availableLlmModelIds[0] ?? "";
  const firstEmb = availableEmbeddingModelIds[0] ?? "";
  const thesisLlm = availableLlmModelIds.find((id) => id === THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID) ?? "";
  const thesisEmb = availableEmbeddingModelIds.find((id) => id === THESIS_DEFAULT_EMBEDDING_MODEL_ID) ?? "";
  if (kind === "LLM_JUDGE_QA") {
    if (next.llmModelIds.length === 0 && next.llmModelId.trim()) {
      next = { ...next, llmModelIds: [next.llmModelId.trim()], llmModelId: "" };
    }
    if (next.llmModelIds.length === 0 && firstLlm) {
      next = { ...next, llmModelIds: [firstLlm] };
    }
  } else if (!next.llmModelId.trim() && (thesisLlm || firstLlm)) {
    next = { ...next, llmModelId: thesisLlm || firstLlm };
  }
  if (!next.embeddingModelId.trim() && (thesisEmb || firstEmb)) {
    next = { ...next, embeddingModelId: thesisEmb || firstEmb };
  }
  if (next.embeddingModelIds.length === 0 && (thesisEmb || firstEmb)) {
    next = { ...next, embeddingModelIds: [thesisEmb || firstEmb] };
  }
  if (kind === "RAG_PRESET_END_TO_END") {
    const primary = next.llmModelId.trim();
    const secondary = next.benchmarkRuntimeParameters.secondaryLlmModelId?.trim() ?? "";
    const thesisSecondary =
      availableLlmModelIds.find(
        (id) => id === THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID && id !== primary,
      ) ?? "";
    if (!secondary && thesisSecondary) {
      next = {
        ...next,
        benchmarkRuntimeParameters: {
          ...next.benchmarkRuntimeParameters,
          secondaryLlmModelId: thesisSecondary,
        },
      };
    }
  }
  return next;
}

function readNumber(src: Record<string, unknown>, ...keys: string[]): number | undefined {
  for (const key of keys) {
    const raw = src[key];
    if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  }
  return undefined;
}

function parseBenchmarkRuntimeParameters(raw: unknown): LabBenchmarkRuntimeParameters {
  if (!raw || typeof raw !== "object") return {};
  const src = raw as Record<string, unknown>;
  const out: LabBenchmarkRuntimeParameters = {};
  const temperature = readNumber(src, "temperature");
  if (temperature != null) out.temperature = temperature;
  const topP = readNumber(src, "topP", "top_p");
  if (topP != null) out.topP = topP;
  const seed = readNumber(src, "seed");
  if (seed != null) out.seed = Math.trunc(seed);
  const maxTokens = readNumber(src, "maxTokens", "max_tokens");
  if (maxTokens != null) out.maxTokens = Math.trunc(maxTokens);
  const presencePenalty = readNumber(src, "presencePenalty", "presence_penalty");
  if (presencePenalty != null) out.presencePenalty = presencePenalty;
  const frequencyPenalty = readNumber(src, "frequencyPenalty", "frequency_penalty");
  if (frequencyPenalty != null) out.frequencyPenalty = frequencyPenalty;
  const responseFormatRaw = src.responseFormat ?? src.response_format;
  if (responseFormatRaw && typeof responseFormatRaw === "object") {
    const type = (responseFormatRaw as Record<string, unknown>).type;
    if (type === "json_object") out.responseFormat = "json_object";
  } else if (responseFormatRaw === "json_object") {
    out.responseFormat = "json_object";
  }
  if (Array.isArray(src.stop)) {
    const stop = src.stop.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
    if (stop.length > 0) out.stop = stop;
  }
  if (src.think === true) out.think = true;
  const topK = readNumber(src, "topK", "top_k");
  if (topK != null) out.topK = Math.trunc(topK);
  const similarityThreshold = readNumber(src, "similarityThreshold", "similarity_threshold");
  if (similarityThreshold != null) out.similarityThreshold = similarityThreshold;
  Object.assign(out, parseEmbeddingBenchmarkRuntimeParameters(src));
  if (typeof src.secondaryLlmModelId === "string" && src.secondaryLlmModelId.trim()) {
    out.secondaryLlmModelId = src.secondaryLlmModelId.trim();
  }
  return out;
}

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
    followMode: "sse",
    lastEvaluationRunId: null,
    corpusId: null,
    autoReindex: true,
    reuseCompatibleActiveSnapshot: true,
    benchmarkRuntimeParameters: {},
  };
}

/** Lab drafts always use SSE; legacy poll/unknown stored values are normalized on load. */
function coerceFollowMode(raw: unknown): LabJobFollowMode {
  void raw;
  return "sse";
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

function finalizeLoadedDraft(kind: LabEvaluationDraftKind, stored: LabEvaluationDraftStored): LabEvaluationDraftStored {
  const stripped: LabEvaluationDraftStored = { ...stored, ...stripLegacyStaleLabModelIds(stored) };
  if (kind !== "RAG_PRESET_END_TO_END") {
    if (draftModelFieldsChanged(stored, stripped)) {
      saveLabEvaluationDraft(kind, stripped);
    }
    return stripped;
  }
  const { selected, removed } = sanitizeLabBenchmarkDraftPresetCodes(
    stripped.selectedExperimentalPresetCodes,
    undefined,
    false,
  );
  const presetSanitized: LabEvaluationDraftStored = { ...stripped, selectedExperimentalPresetCodes: selected };
  if (removed.length === 0 && !draftModelFieldsChanged(stored, presetSanitized)) {
    return presetSanitized;
  }
  saveLabEvaluationDraft(kind, presetSanitized);
  return presetSanitized;
}

/** Loads draft and reports preset codes removed by static single-turn sanitation (P13/P14). */
export function loadLabEvaluationDraftWithSanitationReport(kind: LabEvaluationDraftKind): {
  draft: LabEvaluationDraftStored;
  removedPresets: string[];
} {
  const key = labEvaluationDraftStorageKey(kind);
  let rawCodes: readonly string[] = [];
  try {
    const raw = localStorage.getItem(key);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<LabEvaluationDraftStored>;
      if (Array.isArray(parsed.selectedExperimentalPresetCodes)) {
        rawCodes = parsed.selectedExperimentalPresetCodes.filter((x): x is string => typeof x === "string");
      }
    }
  } catch {
    /* ignore */
  }
  const draft = loadLabEvaluationDraft(kind);
  if (kind !== "RAG_PRESET_END_TO_END") {
    return { draft, removedPresets: [] };
  }
  const { removed } = sanitizeLabBenchmarkDraftPresetCodes(rawCodes, undefined, false);
  return { draft, removedPresets: removed };
}

export function loadLabEvaluationDraft(kind: LabEvaluationDraftKind): LabEvaluationDraftStored {
  const key = labEvaluationDraftStorageKey(kind);
  try {
    const rawNew = localStorage.getItem(key);
    if (rawNew) {
      const parsed = JSON.parse(rawNew) as Record<string, unknown>;
      if (parsed && typeof parsed === "object" && parsed.v === 1) {
        const d = defaultLabEvaluationDraft();
        return finalizeLoadedDraft(kind, {
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
          corpusId:
            typeof parsed.corpusId === "string"
              ? parsed.corpusId
              : parsed.corpusId === null
                ? null
                : d.corpusId,
          autoReindex: typeof parsed.autoReindex === "boolean" ? parsed.autoReindex : d.autoReindex,
          reuseCompatibleActiveSnapshot:
            typeof parsed.reuseCompatibleActiveSnapshot === "boolean"
              ? parsed.reuseCompatibleActiveSnapshot
              : d.reuseCompatibleActiveSnapshot,
          benchmarkRuntimeParameters: parseBenchmarkRuntimeParameters(parsed.benchmarkRuntimeParameters),
        });
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
      return finalizeLoadedDraft(kind, stored);
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
  const id = input.draft.datasetId?.trim();

  const allKnownIds = new Set(input.allDatasetRows.map((r) => r.id));
  const compatibleIds = new Set(input.compatibleDatasetRows.map((r) => r.id));

  let datasetDeletedOrUnknown = false;
  let datasetIncompatibleWithBenchmark = false;

  if (id) {
    if (input.datasetsFetched && !allKnownIds.has(id)) {
      datasetDeletedOrUnknown = true;
    } else if (input.datasetsFetched && allKnownIds.has(id) && !compatibleIds.has(id)) {
      datasetIncompatibleWithBenchmark = true;
    }
  }

  const llmSet = new Set(input.availableLlmModelIds);
  const embSet = new Set(input.availableEmbeddingModelIds);

  const llmModelInvalid =
    input.draft.llmModelId.trim() !== "" &&
    input.kind !== "EMBEDDING_RETRIEVAL" &&
    input.kind !== "LLM_JUDGE_QA" &&
    !isLegacyStaleLabLlmModelId(input.draft.llmModelId)
      ? !llmSet.has(input.draft.llmModelId.trim())
      : false;

  const llmModelsInvalid =
    input.kind === "LLM_JUDGE_QA"
      ? input.draft.llmModelIds.filter(
          (m) => m.trim() !== "" && !isLegacyStaleLabLlmModelId(m) && !llmSet.has(m.trim()),
        )
      : [];

  const embeddingNeedsValidation =
    input.kind === "EMBEDDING_RETRIEVAL" || input.kind === "RAG_PRESET_END_TO_END";
  const embeddingModelInvalid =
    embeddingNeedsValidation &&
    input.kind !== "EMBEDDING_RETRIEVAL" &&
    input.draft.embeddingModelId.trim() !== "" &&
    !isLegacyStaleLabEmbeddingModelId(input.draft.embeddingModelId)
      ? !embSet.has(input.draft.embeddingModelId.trim())
      : false;
  const embeddingModelsInvalid =
    input.kind === "EMBEDDING_RETRIEVAL"
      ? input.draft.embeddingModelIds.filter(
          (m) => m.trim() !== "" && !isLegacyStaleLabEmbeddingModelId(m) && !embSet.has(m.trim()),
        )
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
