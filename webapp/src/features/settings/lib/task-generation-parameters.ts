import type { LabResponseFormat } from "@/features/lab/lib/lab-generation-hyperparameters";
import {
  clampNumber,
  formatStopSequencesText,
  parseStopSequencesText,
} from "@/features/lab/lib/lab-generation-hyperparameters";

export type TaskResponseFormat = LabResponseFormat;

export type TaskGenerationParameters = {
  temperature?: number;
  topP?: number;
  seed?: number;
  maxTokens?: number;
  presencePenalty?: number;
  frequencyPenalty?: number;
  responseFormat?: TaskResponseFormat;
  stopSequences?: string[];
  think?: boolean;
  timeoutSeconds?: number;
};

export type TaskModelRoleForm = {
  role: string;
  roleId: string;
  label: string;
  inheritModel: boolean;
  modelId: string;
  inheritParameters: boolean;
  parameters: TaskGenerationParameters;
  hasOverride?: boolean;
};

export const TASK_LLM_OVERRIDES_KEY = "taskLlmOverrides";

export function defaultParametersFromCatalog(
  catalogDefaults?: Record<string, unknown>,
): TaskGenerationParameters {
  if (!catalogDefaults) return { think: false, responseFormat: "text" };
  return {
    temperature: readNumber(catalogDefaults.temperature),
    topP: readNumber(catalogDefaults.topP),
    seed: readNumber(catalogDefaults.seed),
    maxTokens: readNumber(catalogDefaults.maxTokens),
    presencePenalty: readNumber(catalogDefaults.presencePenalty),
    frequencyPenalty: readNumber(catalogDefaults.frequencyPenalty),
    responseFormat: readResponseFormat(catalogDefaults.responseFormat),
    stopSequences: readStop(catalogDefaults.stopSequences ?? catalogDefaults.stop),
    think: catalogDefaults.think === true,
    timeoutSeconds: readNumber(catalogDefaults.timeoutSeconds),
  };
}

export function parametersToMap(params: TaskGenerationParameters): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (typeof params.temperature === "number") out.temperature = params.temperature;
  if (typeof params.topP === "number") out.topP = clampNumber(params.topP, 0, 1);
  if (typeof params.seed === "number") out.seed = Math.trunc(params.seed);
  if (typeof params.maxTokens === "number") out.maxTokens = Math.max(1, Math.trunc(params.maxTokens));
  if (typeof params.presencePenalty === "number") out.presencePenalty = clampNumber(params.presencePenalty, -2, 2);
  if (typeof params.frequencyPenalty === "number") out.frequencyPenalty = clampNumber(params.frequencyPenalty, -2, 2);
  if (params.responseFormat && params.responseFormat !== "text") out.responseFormat = params.responseFormat;
  if (params.stopSequences && params.stopSequences.length > 0) out.stopSequences = params.stopSequences;
  if (params.think === true) out.think = true;
  if (typeof params.timeoutSeconds === "number") out.timeoutSeconds = Math.max(1, Math.trunc(params.timeoutSeconds));
  return out;
}

export function readTaskModelRolesFromConfig(
  values: Record<string, unknown> | undefined,
  catalogTasks: Array<{
    id: string;
    role?: string;
    label: string;
    inheritsMainModelByDefault: boolean;
    defaultModelId?: string;
    defaultParameters?: Record<string, unknown>;
    settingsVisible?: boolean;
  }>,
): TaskModelRoleForm[] {
  const nested = values?.[TASK_LLM_OVERRIDES_KEY];
  const overrides =
    nested && typeof nested === "object" && !Array.isArray(nested)
      ? (nested as Record<string, unknown>)
      : {};

  const visibleTasks = catalogTasks.filter((task) => task.settingsVisible !== false);

  return visibleTasks.map((task) => {
    const defaults = defaultParametersFromCatalog(task.defaultParameters);
    const row =
      overrides[task.id] && typeof overrides[task.id] === "object" && !Array.isArray(overrides[task.id])
        ? (overrides[task.id] as Record<string, unknown>)
        : {};
    const inheritModel =
      typeof row.inheritModel === "boolean"
        ? row.inheritModel
        : task.inheritsMainModelByDefault && !(typeof row.model === "string" && row.model.trim());
    const inheritParameters =
      typeof row.inheritParameters === "boolean" ? row.inheritParameters : !hasParameterOverrides(row);
    const modelId =
      inheritModel
        ? task.defaultModelId ?? ""
        : typeof row.model === "string" && row.model.trim()
          ? row.model.trim()
          : task.defaultModelId ?? "";
    const parameters = inheritParameters
      ? defaults
      : {
          ...defaults,
          temperature: readNumber(row.temperature) ?? defaults.temperature,
          topP: readNumber(row.topP) ?? defaults.topP,
          seed: readNumber(row.seed) ?? defaults.seed,
          maxTokens: readNumber(row.maxTokens) ?? defaults.maxTokens,
          presencePenalty: readNumber(row.presencePenalty) ?? defaults.presencePenalty,
          frequencyPenalty: readNumber(row.frequencyPenalty) ?? defaults.frequencyPenalty,
          responseFormat: readResponseFormat(row.responseFormat) ?? defaults.responseFormat,
          stopSequences: readStop(row.stopSequences ?? row.stop) ?? defaults.stopSequences,
          think: row.think === true,
          timeoutSeconds: readNumber(row.timeoutSeconds) ?? defaults.timeoutSeconds,
        };
    return {
      role: task.role ?? task.id,
      roleId: task.id,
      label: task.label,
      inheritModel,
      modelId,
      inheritParameters,
      parameters,
      hasOverride: Object.keys(row).length > 0,
    };
  });
}

export function mergeTaskModelRolesIntoConfig(
  base: Record<string, unknown>,
  roles: TaskModelRoleForm[],
): Record<string, unknown> {
  const overrides: Record<string, unknown> = {};
  for (const role of roles) {
    if (!role.hasOverride && !role.inheritModel && !role.inheritParameters) {
      continue;
    }
    const row: Record<string, unknown> = { enabled: true };
    row.inheritModel = role.inheritModel;
    row.inheritParameters = role.inheritParameters;
    if (!role.inheritModel && role.modelId.trim()) row.model = role.modelId.trim();
    if (!role.inheritParameters) Object.assign(row, parametersToMap(role.parameters));
    if (row.inheritModel === true && row.inheritParameters === true && Object.keys(row).length <= 3) {
      continue;
    }
    overrides[role.roleId] = row;
  }
  const next = { ...base };
  if (Object.keys(overrides).length === 0) {
    delete next[TASK_LLM_OVERRIDES_KEY];
  } else {
    next[TASK_LLM_OVERRIDES_KEY] = overrides;
  }
  return next;
}

export function resetRoleToDefaults(
  role: TaskModelRoleForm,
  catalogTask?: {
    inheritsMainModelByDefault: boolean;
    defaultModelId?: string;
    defaultParameters?: Record<string, unknown>;
  },
): TaskModelRoleForm {
  const defaults = defaultParametersFromCatalog(catalogTask?.defaultParameters);
  return {
    ...role,
    inheritModel: catalogTask?.inheritsMainModelByDefault ?? false,
    modelId: catalogTask?.defaultModelId ?? role.modelId,
    inheritParameters: false,
    parameters: defaults,
    hasOverride: false,
  };
}

export function formatRoleSummary(role: TaskModelRoleForm): string {
  const parts = [
    role.role,
    role.inheritModel ? "inherits model" : role.modelId,
    `temp ${role.parameters.temperature ?? "-"}`,
    `max ${role.parameters.maxTokens ?? "-"}`,
    role.parameters.responseFormat ?? "text",
  ];
  return parts.join(" · ");
}

export { formatStopSequencesText, parseStopSequencesText, clampNumber };

function hasParameterOverrides(row: Record<string, unknown>): boolean {
  return (
    row.temperature !== undefined ||
    row.topP !== undefined ||
    row.maxTokens !== undefined ||
    row.seed !== undefined ||
    row.presencePenalty !== undefined ||
    row.frequencyPenalty !== undefined ||
    row.responseFormat !== undefined ||
    row.stop !== undefined ||
    row.stopSequences !== undefined ||
    row.think !== undefined ||
    row.timeoutSeconds !== undefined
  );
}

function readNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function readResponseFormat(value: unknown): TaskResponseFormat | undefined {
  if (value === "text" || value === "json_object" || value === "json_schema") return value;
  return undefined;
}

function readStop(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const stop = value.map((s) => String(s).trim()).filter((s) => s.length > 0);
  return stop.length > 0 ? stop : undefined;
}
