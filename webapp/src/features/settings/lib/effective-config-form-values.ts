import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import {
  LLM_ADDITIONAL_PARAMETERS_KEY,
  LLM_TEMPERATURE_KEY,
  appliedModelParameters,
  type LlmProviderKind,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import { readAdditionalParameters, readTemperature } from "@/features/settings/lib/llm-additional-parameters";
import { pickFormValues } from "@/features/settings/lib/rag-config-values";
import type {
  MeEffectiveEmbeddingDefaultsResponse,
  MeEffectiveLlmDefaultsResponse,
} from "@/types/api";

/** Top-level keys cleared by Reset LLM defaults. */
export const LLM_RESET_TOP_LEVEL_KEYS = [
  "llmModel",
  "classifierModelId",
  LLM_TEMPERATURE_KEY,
  "temperature",
  LLM_ADDITIONAL_PARAMETERS_KEY,
] as const;

/** Top-level keys cleared by Reset embedding defaults (retrieval params have dedicated keys). */
export const EMBEDDING_RESET_TOP_LEVEL_KEYS = [
  "embeddingModel",
  "embeddingEncodingFormat",
  "embeddingDimensions",
  "embeddingTimeoutSeconds",
  "embeddingBatchSize",
  "embeddingMaxInputChars",
  "embeddingNormalize",
  "embeddingTruncate",
] as const;

/** Top-level keys cleared by Reset retrieval defaults. */
export const RETRIEVAL_RESET_TOP_LEVEL_KEYS = ["topK", "similarityThreshold"] as const;

export type SettingsSaveMode = "user" | "project";

export type SettingsSaveContext = {
  mode: SettingsSaveMode;
  stored: Record<string, unknown>;
  values: ConfigFormValues;
  additionalParameters: Record<string, unknown>;
  editableKeys: string[];
  llmEffective: MeEffectiveLlmDefaultsResponse | undefined;
  embeddingEffective: MeEffectiveEmbeddingDefaultsResponse | undefined;
  /** User stored overrides — inheritance baseline for project-mode saves. */
  userStored?: Record<string, unknown>;
  provider: LlmProviderKind | null;
};

export type EffectiveFormMergeResult = {
  formValues: ConfigFormValues;
  additionalParameters: Record<string, unknown>;
};

function isBlank(value: unknown): boolean {
  return value === undefined || value === null || value === "";
}

function valuesEqual(a: unknown, b: unknown): boolean {
  if (isBlank(a) && isBlank(b)) return true;
  return a === b;
}

function readEffectiveAdditional(
  effective: MeEffectiveLlmDefaultsResponse | undefined,
  key: string,
): unknown {
  const additional = effective?.additionalParameters;
  if (!additional || typeof additional !== "object") return undefined;
  return (additional as Record<string, unknown>)[key];
}

export function readEffectiveLlmParameter(
  config: Record<string, unknown> | undefined,
  effective: MeEffectiveLlmDefaultsResponse | undefined,
  provider: LlmProviderKind | null,
  storage: "topLevel" | "additional",
  configKey: string,
): unknown {
  if (storage === "topLevel" && configKey === LLM_TEMPERATURE_KEY) {
    const configured = readTemperature(config);
    if (!isBlank(configured)) return configured;
    return effective?.temperature ?? undefined;
  }
  if (storage === "additional") {
    const configured = readAdditionalParameters(config)[configKey];
    if (!isBlank(configured)) return configured;
    return readEffectiveAdditional(effective, configKey);
  }
  const configured = config?.[configKey];
  if (!isBlank(configured)) return configured;
  if (configKey === "llmModel") return effective?.chatModel ?? undefined;
  if (configKey === "classifierModelId") return effective?.classifierModelId ?? undefined;
  return undefined;
}

export function readEffectiveEmbeddingField(
  config: Record<string, unknown> | undefined,
  effective: MeEffectiveEmbeddingDefaultsResponse | undefined,
  key: string,
): unknown {
  const configured = config?.[key];
  if (!isBlank(configured)) return configured;
  if (!effective) return undefined;
  switch (key) {
    case "embeddingModel":
      return effective.embeddingModel ?? undefined;
    case "embeddingEncodingFormat":
      return effective.embeddingOptions?.encodingFormat ?? undefined;
    case "embeddingDimensions":
      return effective.embeddingOptions?.dimensions ?? undefined;
    case "embeddingTimeoutSeconds":
      return effective.embeddingOptions?.timeoutSeconds ?? undefined;
    case "topK":
      return effective.retrievalOptions?.topK ?? undefined;
    case "similarityThreshold":
      return effective.retrievalOptions?.similarityThreshold ?? undefined;
    case "materializationStrategy":
      return effective.retrievalOptions?.materializationStrategy ?? undefined;
    case "embeddingBatchSize":
      return effective.indexingOptions?.batchSize ?? undefined;
    case "embeddingMaxInputChars":
      return effective.indexingOptions?.maxInputChars ?? undefined;
    case "embeddingNormalize":
      return effective.indexingOptions?.normalize ?? undefined;
    case "embeddingTruncate":
      return effective.indexingOptions?.truncate ?? undefined;
    default:
      return undefined;
  }
}

export function isFieldInherited(
  config: Record<string, unknown> | undefined,
  key: string,
): boolean {
  return isBlank(config?.[key]);
}

export function isAdditionalParameterInherited(
  config: Record<string, unknown> | undefined,
  configKey: string,
): boolean {
  return isBlank(readAdditionalParameters(config)[configKey]);
}

export function mergeEffectiveIntoFormValues(
  config: Record<string, unknown> | undefined,
  editableKeys: string[],
  llmEffective: MeEffectiveLlmDefaultsResponse | undefined,
  embeddingEffective: MeEffectiveEmbeddingDefaultsResponse | undefined,
  provider: LlmProviderKind | null,
  mode: SettingsSaveMode = "user",
): EffectiveFormMergeResult {
  const picked = pickFormValues(config, editableKeys);
  const additional = { ...readAdditionalParameters(config) };

  for (const def of appliedModelParameters(provider)) {
    const effectiveValue = readEffectiveLlmParameter(config, llmEffective, provider, def.storage, def.configKey);
    if (def.storage === "topLevel" && def.configKey === LLM_TEMPERATURE_KEY) {
      if (editableKeys.includes(LLM_TEMPERATURE_KEY) && isBlank(picked[LLM_TEMPERATURE_KEY]) && !isBlank(effectiveValue)) {
        picked[LLM_TEMPERATURE_KEY] = effectiveValue as number;
      }
    } else if (
      def.storage === "additional" &&
      editableKeys.includes(LLM_ADDITIONAL_PARAMETERS_KEY) &&
      isBlank(additional[def.configKey]) &&
      !isBlank(effectiveValue)
    ) {
      additional[def.configKey] = effectiveValue;
    }
  }

  for (const key of [...EMBEDDING_RESET_TOP_LEVEL_KEYS, ...RETRIEVAL_RESET_TOP_LEVEL_KEYS]) {
    if (!editableKeys.includes(key)) continue;
    if (mode === "project" && RETRIEVAL_RESET_TOP_LEVEL_KEYS.includes(key as (typeof RETRIEVAL_RESET_TOP_LEVEL_KEYS)[number])) {
      continue;
    }
    const effectiveValue = readEffectiveEmbeddingField(config, embeddingEffective, key);
    if (isBlank(picked[key]) && !isBlank(effectiveValue)) {
      picked[key] = effectiveValue as string | number | boolean;
    }
  }

  if (editableKeys.includes("llmModel") && isBlank(picked.llmModel) && llmEffective?.chatModel) {
    picked.llmModel = llmEffective.chatModel;
  }
  if (editableKeys.includes("classifierModelId") && isBlank(picked.classifierModelId) && llmEffective?.classifierModelId) {
    picked.classifierModelId = llmEffective.classifierModelId;
  }

  return { formValues: picked, additionalParameters: additional };
}

function readInheritanceBaseline(
  key: string,
  mode: SettingsSaveMode,
  userStored: Record<string, unknown> | undefined,
  embeddingEffective: MeEffectiveEmbeddingDefaultsResponse | undefined,
  llmEffective: MeEffectiveLlmDefaultsResponse | undefined,
  provider: LlmProviderKind | null,
): unknown {
  if (
    mode === "project"
    && RETRIEVAL_RESET_TOP_LEVEL_KEYS.includes(key as (typeof RETRIEVAL_RESET_TOP_LEVEL_KEYS)[number])
  ) {
    return undefined;
  }
  if (mode === "project" && userStored && !isBlank(userStored[key])) {
    return userStored[key];
  }
  if (RETRIEVAL_RESET_TOP_LEVEL_KEYS.includes(key as (typeof RETRIEVAL_RESET_TOP_LEVEL_KEYS)[number])
    || EMBEDDING_RESET_TOP_LEVEL_KEYS.includes(key as (typeof EMBEDDING_RESET_TOP_LEVEL_KEYS)[number])) {
    return readEffectiveEmbeddingField(undefined, embeddingEffective, key);
  }
  if (key === LLM_TEMPERATURE_KEY || key === "temperature") {
    return llmEffective?.temperature;
  }
  if (key === "llmModel") {
    return llmEffective?.chatModel;
  }
  if (key === "classifierModelId") {
    return llmEffective?.classifierModelId;
  }
  for (const def of appliedModelParameters(provider)) {
    if (def.storage === "additional" && def.configKey === key) {
      return readEffectiveAdditional(llmEffective, key);
    }
  }
  return undefined;
}

/** Builds a merge patch for stored overrides only (null removes a key). */
export function buildStoredOverridesPatch(ctx: SettingsSaveContext): Record<string, unknown> {
  const patch: Record<string, unknown> = {};
  const { stored, values, editableKeys, mode, userStored, embeddingEffective, llmEffective, provider } = ctx;

  const retrievalKeys = RETRIEVAL_RESET_TOP_LEVEL_KEYS.filter((key) => editableKeys.includes(key));
  if (retrievalKeys.length === RETRIEVAL_RESET_TOP_LEVEL_KEYS.length) {
    const topK = values.topK;
    const threshold = values.similarityThreshold;
    if (typeof topK === "number" && typeof threshold === "number") {
      const matchesStored =
        valuesEqual(topK, stored.topK) && valuesEqual(threshold, stored.similarityThreshold);
      if (matchesStored) {
        // unchanged — do not clear overrides that happen to match inheritance baseline
      } else if (mode === "project") {
        patch.topK = topK;
        patch.similarityThreshold = threshold;
      } else {
        const baselineTopK = readInheritanceBaseline(
          "topK",
          mode,
          userStored,
          embeddingEffective,
          llmEffective,
          provider,
        );
        const baselineThreshold = readInheritanceBaseline(
          "similarityThreshold",
          mode,
          userStored,
          embeddingEffective,
          llmEffective,
          provider,
        );
        const matchesBaseline =
          valuesEqual(topK, baselineTopK) && valuesEqual(threshold, baselineThreshold);
        if (matchesBaseline) {
          if (!isBlank(stored.topK)) {
            patch.topK = null;
          }
          if (!isBlank(stored.similarityThreshold)) {
            patch.similarityThreshold = null;
          }
        } else {
          patch.topK = topK;
          patch.similarityThreshold = threshold;
        }
      }
    }
  }

  for (const key of editableKeys) {
    if (key === LLM_ADDITIONAL_PARAMETERS_KEY) {
      continue;
    }
    if (RETRIEVAL_RESET_TOP_LEVEL_KEYS.includes(key as (typeof RETRIEVAL_RESET_TOP_LEVEL_KEYS)[number])) {
      continue;
    }
    const submitted = values[key];
    if (submitted === undefined) {
      continue;
    }
    const storedValue = stored[key];
    if (valuesEqual(submitted, storedValue)) {
      continue;
    }
    const baseline = readInheritanceBaseline(
      key,
      mode,
      userStored,
      embeddingEffective,
      llmEffective,
      provider,
    );
    if (valuesEqual(submitted, baseline)) {
      if (!isBlank(storedValue)) {
        patch[key] = null;
      }
      continue;
    }
    if (typeof submitted === "string" && submitted.trim() === "") {
      if (!isBlank(storedValue)) {
        patch[key] = null;
      }
      continue;
    }
    patch[key] = submitted;
  }

  const cleanedAdditional: Record<string, unknown> = {};
  for (const def of appliedModelParameters(provider)) {
    if (def.storage !== "additional") continue;
    const submitted = ctx.additionalParameters[def.configKey];
    const effectiveValue = readEffectiveAdditional(llmEffective, def.configKey);
    const storedAdditional = readAdditionalParameters(stored)[def.configKey];
    if (isBlank(submitted)) {
      if (!isBlank(storedAdditional)) {
        cleanedAdditional[def.configKey] = null;
      }
      continue;
    }
    if (valuesEqual(submitted, storedAdditional)) {
      continue;
    }
    if (valuesEqual(submitted, effectiveValue)) {
      if (!isBlank(storedAdditional)) {
        cleanedAdditional[def.configKey] = null;
      }
      continue;
    }
    cleanedAdditional[def.configKey] = submitted;
  }

  if (editableKeys.includes(LLM_ADDITIONAL_PARAMETERS_KEY) && Object.keys(cleanedAdditional).length > 0) {
    const mergedAdditional = { ...readAdditionalParameters(stored) };
    for (const [k, v] of Object.entries(cleanedAdditional)) {
      if (v === null) {
        delete mergedAdditional[k];
      } else {
        mergedAdditional[k] = v;
      }
    }
    if (Object.keys(mergedAdditional).length === 0) {
      patch[LLM_ADDITIONAL_PARAMETERS_KEY] = null;
    } else {
      patch[LLM_ADDITIONAL_PARAMETERS_KEY] = mergedAdditional;
    }
  }

  return patch;
}

/** @deprecated Use buildStoredOverridesPatch with SettingsSaveContext. */
export function buildSavePayloadRespectingEffectiveDefaults(
  base: Record<string, unknown> | undefined,
  values: ConfigFormValues,
  additionalParameters: Record<string, unknown>,
  editableKeys: string[],
  llmEffective: MeEffectiveLlmDefaultsResponse | undefined,
  embeddingEffective: MeEffectiveEmbeddingDefaultsResponse | undefined,
  provider: LlmProviderKind | null,
  mode: SettingsSaveMode = "user",
  userStored?: Record<string, unknown>,
): Record<string, unknown> {
  return buildStoredOverridesPatch({
    mode,
    stored: base ?? {},
    values,
    additionalParameters,
    editableKeys,
    llmEffective,
    embeddingEffective,
    userStored,
    provider,
  });
}

export function clearConfigOverrideKeys(
  config: Record<string, unknown> | undefined,
  keys: readonly string[],
): Record<string, unknown> {
  const next = config ? { ...config } : {};
  for (const key of keys) {
    delete next[key];
  }
  return next;
}
