/** Matches backend {@link com.uniovi.rag.domain.llm.LlmProvider} values exposed to the webapp. */
export type LlmProviderKind = "OPENAI_COMPATIBLE" | "OLLAMA_NATIVE";

export const LLM_ADDITIONAL_PARAMETERS_KEY = "llmAdditionalParameters";
export const LLM_TEMPERATURE_KEY = "llmTemperature";

export type ModelParameterStorage = "topLevel" | "additional";

export type ModelParameterDef = Readonly<{
  id: string;
  configKey: string;
  storage: ModelParameterStorage;
  labelKey: string;
  type: "number" | "integer";
  min?: number;
  max?: number;
  /** Whether RagLlmChatInvoker + provider mapper apply this parameter at runtime. */
  applied: Readonly<Record<LlmProviderKind, boolean>>;
}>;

/**
 * Provider parameter catalog aligned with OpenAiCompatibleChatMapper (temperature only)
 * and OllamaLlmOptionsMapper (temperature + additionalParameters).
 */
export const MODEL_PARAMETER_CATALOG: readonly ModelParameterDef[] = [
  {
    id: "temperature",
    configKey: LLM_TEMPERATURE_KEY,
    storage: "topLevel",
    labelKey: "modelParamTemperature",
    type: "number",
    min: 0,
    max: 2,
    applied: { OPENAI_COMPATIBLE: true, OLLAMA_NATIVE: true },
  },
  {
    id: "top_p",
    configKey: "topP",
    storage: "additional",
    labelKey: "modelParamTopP",
    type: "number",
    min: 0,
    max: 1,
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: true },
  },
  {
    id: "top_k",
    configKey: "topK",
    storage: "additional",
    labelKey: "modelParamTopK",
    type: "integer",
    min: 1,
    max: 200,
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: true },
  },
  {
    id: "num_ctx",
    configKey: "numCtx",
    storage: "additional",
    labelKey: "modelParamNumCtx",
    type: "integer",
    min: 512,
    max: 1_000_000,
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: true },
  },
  {
    id: "repeat_penalty",
    configKey: "repeatPenalty",
    storage: "additional",
    labelKey: "modelParamRepeatPenalty",
    type: "number",
    min: 0,
    max: 2,
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: true },
  },
  {
    id: "num_predict",
    configKey: "numPredict",
    storage: "additional",
    labelKey: "modelParamNumPredict",
    type: "integer",
    min: 1,
    max: 100_000,
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: true },
  },
  {
    id: "seed",
    configKey: "seed",
    storage: "additional",
    labelKey: "modelParamSeed",
    type: "integer",
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: true },
  },
  {
    id: "max_tokens",
    configKey: "maxTokens",
    storage: "additional",
    labelKey: "modelParamMaxTokens",
    type: "integer",
    min: 1,
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: false },
  },
  {
    id: "presence_penalty",
    configKey: "presencePenalty",
    storage: "additional",
    labelKey: "modelParamPresencePenalty",
    type: "number",
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: false },
  },
  {
    id: "frequency_penalty",
    configKey: "frequencyPenalty",
    storage: "additional",
    labelKey: "modelParamFrequencyPenalty",
    type: "number",
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: false },
  },
  {
    id: "response_format",
    configKey: "responseFormat",
    storage: "additional",
    labelKey: "modelParamResponseFormat",
    type: "number",
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: false },
  },
  {
    id: "stop",
    configKey: "stop",
    storage: "additional",
    labelKey: "modelParamStop",
    type: "number",
    applied: { OPENAI_COMPATIBLE: false, OLLAMA_NATIVE: false },
  },
] as const;

export function normalizeLlmProvider(provider: string | undefined | null): LlmProviderKind | null {
  if (provider === "OPENAI_COMPATIBLE" || provider === "OLLAMA_NATIVE") return provider;
  return null;
}

export function appliedModelParameters(provider: LlmProviderKind | null): ModelParameterDef[] {
  if (!provider) return MODEL_PARAMETER_CATALOG.filter((p) => p.id === "temperature");
  return MODEL_PARAMETER_CATALOG.filter((p) => p.applied[provider]);
}

export function unsupportedModelParameters(provider: LlmProviderKind | null): ModelParameterDef[] {
  if (!provider) {
    return MODEL_PARAMETER_CATALOG.filter((p) => p.id !== "temperature");
  }
  return MODEL_PARAMETER_CATALOG.filter((p) => !p.applied[provider]);
}

export function appliedModelParameterIds(provider: LlmProviderKind | null): Set<string> {
  return new Set(appliedModelParameters(provider).map((p) => p.id));
}
