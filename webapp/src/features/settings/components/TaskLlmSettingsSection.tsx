"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useTaskLlmCatalogQuery } from "@/features/settings/hooks/use-prompt-catalog";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";

export const TASK_LLM_OVERRIDES_KEY = "taskLlmOverrides";

export type TaskLlmOverrideForm = {
  enabled?: boolean;
  model?: string;
  temperature?: number;
};

export function readTaskLlmOverrides(
  values: Record<string, unknown> | undefined,
): Record<string, TaskLlmOverrideForm> {
  if (!values) return {};
  const nested = values[TASK_LLM_OVERRIDES_KEY];
  if (!nested || typeof nested !== "object" || Array.isArray(nested)) return {};
  const out: Record<string, TaskLlmOverrideForm> = {};
  for (const [k, v] of Object.entries(nested as Record<string, unknown>)) {
    if (!v || typeof v !== "object" || Array.isArray(v)) continue;
    const row = v as Record<string, unknown>;
    out[k] = {
      enabled: typeof row.enabled === "boolean" ? row.enabled : undefined,
      model: typeof row.model === "string" ? row.model : undefined,
      temperature: typeof row.temperature === "number" ? row.temperature : undefined,
    };
  }
  return out;
}

export function mergeTaskLlmOverrides(
  base: Record<string, unknown>,
  overrides: Record<string, TaskLlmOverrideForm>,
): Record<string, unknown> {
  const cleaned: Record<string, TaskLlmOverrideForm> = {};
  for (const [k, v] of Object.entries(overrides)) {
    if (v.model?.trim() || v.temperature !== undefined || v.enabled === false) {
      cleaned[k] = v;
    }
  }
  const next = { ...base };
  if (Object.keys(cleaned).length === 0) {
    delete next[TASK_LLM_OVERRIDES_KEY];
  } else {
    next[TASK_LLM_OVERRIDES_KEY] = cleaned;
  }
  return next;
}

type TaskLlmSettingsSectionProps = Readonly<{
  configValues: Record<string, unknown>;
  onChange: (next: Record<string, unknown>) => void;
}>;

export function TaskLlmSettingsSection({ configValues, onChange }: TaskLlmSettingsSectionProps) {
  const t = useTranslations("Settings");
  const catalogQ = useTaskLlmCatalogQuery();
  const modelsQ = useMeSelectableLlmModels("CHAT");

  const overrides = useMemo(() => readTaskLlmOverrides(configValues), [configValues]);

  function patchTask(taskId: string, patch: Partial<TaskLlmOverrideForm>) {
    const current = overrides[taskId] ?? {};
    const next = { ...overrides, [taskId]: { ...current, ...patch } };
    onChange(mergeTaskLlmOverrides(configValues, next));
  }

  if (catalogQ.isLoading) {
    return <p className="text-muted-foreground text-sm">{t("configLoading")}</p>;
  }

  return (
    <div className="flex flex-col gap-4" data-testid="task-llm-settings">
      <div>
        <h4 className="text-sm font-medium">{t("taskLlmSettingsTitle")}</h4>
        <p className="text-muted-foreground mt-1 text-xs">{t("taskLlmSettingsDescription")}</p>
      </div>
      {(catalogQ.data?.tasks ?? []).map((task) => {
        const row = overrides[task.id] ?? {};
        const inherited = task.inheritsMainModelByDefault && !row.model?.trim();
        return (
          <div
            key={task.id}
            className="rounded-md border bg-muted/20 p-3"
            data-testid={`task-llm-row-${task.id}`}
          >
            <p className="text-sm font-medium">{task.label}</p>
            <p className="text-muted-foreground text-xs">
              {inherited ? t("taskLlmInheritsMainModel") : t("taskLlmCustomModel")}
            </p>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor={`task-model-${task.id}`}>{t("taskLlmModelLabel")}</Label>
                <select
                  id={`task-model-${task.id}`}
                  data-testid={`task-llm-model-select-${task.id}`}
                  className="border-input bg-background h-10 w-full rounded-md border px-3 py-2 text-sm"
                  value={row.model ?? ""}
                  disabled={modelsQ.isLoading}
                  onChange={(e) => patchTask(task.id, { model: e.target.value })}
                >
                  <option value="">{t("taskLlmModelPlaceholder")}</option>
                  {(modelsQ.data?.models ?? []).map((m) => (
                    <option key={m.modelName} value={m.modelName} disabled={!m.selectable}>
                      {m.displayName?.trim() ? m.displayName : m.modelName}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor={`task-temp-${task.id}`}>{t("taskLlmTemperatureLabel")}</Label>
                <Input
                  id={`task-temp-${task.id}`}
                  type="number"
                  step="0.1"
                  min={0}
                  max={2}
                  value={row.temperature ?? ""}
                  placeholder={t("taskLlmTemperaturePlaceholder")}
                  onChange={(e) => {
                    const raw = e.target.value;
                    patchTask(task.id, { temperature: raw === "" ? undefined : Number(raw) });
                  }}
                />
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
