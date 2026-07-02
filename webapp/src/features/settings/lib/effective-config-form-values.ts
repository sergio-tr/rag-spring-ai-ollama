import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import {
  LLM_ADDITIONAL_PARAMETERS_KEY,
  LLM_TEMPERATURE_KEY,
  appliedModelParameters,
  type LlmProviderKind,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import {
  mergeAdditionalParametersIntoPayload,
  readAdditionalParameters,
  readTemperature,
} from "@/features/settings/lib/llm-additional-parameters";
import { mergePayload, pickFormValues } from "@/features/settings/lib/rag-config-values";
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

/** Top-level keys cleared by Reset embedding defaults. */
export const EMBEDDING_RESET_TOP_LEVEL_KEYS = [
  "embeddingModel",
  "embeddingEncodingFormat",
  "embeddingDimensions",
  "embeddingTimeoutSeconds",
  "topK",
  "similarityThreshold",
  "materializationStrategy",
  "embeddingBatchSize",
  "embeddingMaxInputChars",
  "embeddingNormalize",
  "embeddingTruncate",
] as const;

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

  for (const key of EMBEDDING_RESET_TOP_LEVEL_KEYS) {
    if (!editableKeys.includes(key)) continue;
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

export function buildSavePayloadRespectingEffectiveDefaults(
  base: Record<string, unknown> | undefined,
  values: ConfigFormValues,
  additionalParameters: Record<string, unknown>,
  editableKeys: string[],
  llmEffective: MeEffectiveLlmDefaultsResponse | undefined,
  embeddingEffective: MeEffectiveEmbeddingDefaultsResponse | undefined,
  provider: LlmProviderKind | null,
): Record<string, unknown> {
  let payload = mergePayload(base, values, editableKeys);

  const temp = values[LLM_TEMPERATURE_KEY];
  const effectiveTemp = llmEffective?.temperature;
  if (temp === undefined || valuesEqual(temp, effectiveTemp)) {
    delete payload[LLM_TEMPERATURE_KEY];
    delete payload.temperature;
  }

  for (const key of EMBEDDING_RESET_TOP_LEVEL_KEYS) {
    if (!editableKeys.includes(key)) continue;
    const submitted = values[key];
    const effectiveValue = readEffectiveEmbeddingField(base, embeddingEffective, key);
    if (submitted === undefined || valuesEqual(submitted, effectiveValue)) {
      delete payload[key];
    }
  }

  if (editableKeys.includes("llmModel")) {
    const submitted = values.llmModel;
    if (submitted === undefined || valuesEqual(submitted, llmEffective?.chatModel)) {
      delete payload.llmModel;
    }
  }
  if (editableKeys.includes("classifierModelId")) {
    const submitted = values.classifierModelId;
    if (submitted === undefined || valuesEqual(submitted, llmEffective?.classifierModelId)) {
      delete payload.classifierModelId;
    }
  }

  const cleanedAdditional: Record<string, unknown> = {};
  for (const def of appliedModelParameters(provider)) {
    if (def.storage !== "additional") continue;
    const submitted = additionalParameters[def.configKey];
    const effectiveValue = readEffectiveAdditional(llmEffective, def.configKey);
    if (!isBlank(submitted) && !valuesEqual(submitted, effectiveValue)) {
      cleanedAdditional[def.configKey] = submitted;
    }
  }
  payload = mergeAdditionalParametersIntoPayload(payload, cleanedAdditional);
  return payload;
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
