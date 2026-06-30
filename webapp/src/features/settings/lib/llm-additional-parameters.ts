import { LLM_ADDITIONAL_PARAMETERS_KEY, LLM_TEMPERATURE_KEY } from "./provider-aware-llm-parameters";

export function readAdditionalParameters(config: Record<string, unknown> | undefined): Record<string, unknown> {
  const raw = config?.[LLM_ADDITIONAL_PARAMETERS_KEY];
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  return { ...(raw as Record<string, unknown>) };
}

export function readTemperature(config: Record<string, unknown> | undefined): number | undefined {
  const direct = config?.[LLM_TEMPERATURE_KEY];
  if (typeof direct === "number" && Number.isFinite(direct)) return direct;
  const legacy = config?.temperature;
  if (typeof legacy === "number" && Number.isFinite(legacy)) return legacy;
  return undefined;
}

export function mergeAdditionalParametersIntoPayload(
  base: Record<string, unknown>,
  additional: Record<string, unknown>,
): Record<string, unknown> {
  const cleaned: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(additional)) {
    if (value === undefined || value === null || value === "") continue;
    cleaned[key] = value;
  }
  const next = { ...base };
  if (Object.keys(cleaned).length === 0) {
    delete next[LLM_ADDITIONAL_PARAMETERS_KEY];
  } else {
    next[LLM_ADDITIONAL_PARAMETERS_KEY] = cleaned;
  }
  return next;
}

export function readParameterValue(
  config: Record<string, unknown> | undefined,
  storage: "topLevel" | "additional",
  configKey: string,
): unknown {
  if (storage === "topLevel" && configKey === LLM_TEMPERATURE_KEY) {
    return readTemperature(config);
  }
  if (storage === "additional") {
    return readAdditionalParameters(config)[configKey];
  }
  return config?.[configKey];
}
