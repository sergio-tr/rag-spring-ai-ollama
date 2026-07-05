"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";
import { useTaskLlmCatalogQuery } from "@/features/settings/hooks/use-prompt-catalog";
import {
  clampNumber,
  formatRoleSummary,
  formatStopSequencesText,
  mergeTaskModelRolesIntoConfig,
  parseStopSequencesText,
  readTaskModelRolesFromConfig,
  resetRoleToDefaults,
  TASK_LLM_OVERRIDES_KEY,
  type TaskGenerationParameters,
  type TaskModelRoleForm,
} from "@/features/settings/lib/task-generation-parameters";

export { TASK_LLM_OVERRIDES_KEY };

type AdvancedTaskModelSettingsFormProps = Readonly<{
  configValues: Record<string, unknown>;
  onChange: (next: Record<string, unknown>) => void;
}>;

function BoundedNumberField({
  label,
  testId,
  value,
  min,
  max,
  step,
  disabled,
  onCommit,
}: {
  label: string;
  testId: string;
  value: number | undefined;
  min: number;
  max: number;
  step: string;
  disabled?: boolean;
  onCommit: (next: number | undefined) => void;
}) {
  const display = typeof value === "number" && Number.isFinite(value) ? String(value) : "";
  return (
    <label className="min-w-[220px] flex-1 space-y-1 text-xs" data-testid={testId}>
      <span className="text-muted-foreground font-medium">{label}</span>
      <input
        type="number"
        className="bg-background w-full rounded-md border px-2 py-1 text-sm disabled:opacity-50"
        step={step}
        min={min}
        max={max}
        disabled={disabled}
        value={display}
        onChange={(event) => {
          const text = event.target.value.trim();
          if (!text) {
            onCommit(undefined);
            return;
          }
          const parsed = Number(text);
          if (!Number.isFinite(parsed)) return;
          onCommit(clampNumber(parsed, min, max));
        }}
      />
    </label>
  );
}

function RoleParameterFields({
  role,
  onPatch,
  disabled,
  t,
}: {
  role: TaskModelRoleForm;
  onPatch: (patch: Partial<TaskModelRoleForm>) => void;
  disabled?: boolean;
  t: (key: string) => string;
}) {
  const params = role.parameters;
  const thinkEnabled = params.think === true;
  const responseFormat = params.responseFormat ?? "text";

  function patchParams(next: TaskGenerationParameters) {
    onPatch({ parameters: next, inheritParameters: false, hasOverride: true });
  }

  return (
    <div className="flex min-w-0 max-w-full flex-wrap gap-3" data-testid={`task-role-parameters-${role.roleId}`}>
      <BoundedNumberField
        label={t("taskLlmTemperatureLabel")}
        testId={`task-hp-temperature-${role.roleId}`}
        value={params.temperature}
        min={0}
        max={2}
        step="0.1"
        disabled={disabled || role.inheritParameters}
        onCommit={(temperature) => patchParams({ ...params, temperature })}
      />
      <BoundedNumberField
        label={t("taskLlmTopPLabel")}
        testId={`task-hp-top-p-${role.roleId}`}
        value={params.topP}
        min={0}
        max={1}
        step="0.01"
        disabled={disabled || role.inheritParameters}
        onCommit={(topP) => patchParams({ ...params, topP })}
      />
      <BoundedNumberField
        label={t("taskLlmSeedLabel")}
        testId={`task-hp-seed-${role.roleId}`}
        value={params.seed}
        min={-1_000_000_000}
        max={1_000_000_000}
        step="1"
        disabled={disabled || role.inheritParameters}
        onCommit={(seed) => patchParams({ ...params, seed })}
      />
      <BoundedNumberField
        label={t("taskLlmMaxTokensLabel")}
        testId={`task-hp-max-tokens-${role.roleId}`}
        value={params.maxTokens}
        min={1}
        max={1_000_000}
        step="1"
        disabled={disabled || role.inheritParameters}
        onCommit={(maxTokens) => patchParams({ ...params, maxTokens })}
      />
      <BoundedNumberField
        label={t("taskLlmPresencePenaltyLabel")}
        testId={`task-hp-presence-penalty-${role.roleId}`}
        value={params.presencePenalty}
        min={-2}
        max={2}
        step="0.1"
        disabled={disabled || role.inheritParameters}
        onCommit={(presencePenalty) => patchParams({ ...params, presencePenalty })}
      />
      <BoundedNumberField
        label={t("taskLlmFrequencyPenaltyLabel")}
        testId={`task-hp-frequency-penalty-${role.roleId}`}
        value={params.frequencyPenalty}
        min={-2}
        max={2}
        step="0.1"
        disabled={disabled || role.inheritParameters}
        onCommit={(frequencyPenalty) => patchParams({ ...params, frequencyPenalty })}
      />
      <label className="min-w-[220px] flex-1 space-y-1 text-xs" data-testid={`task-hp-response-format-${role.roleId}`}>
        <span className="text-muted-foreground font-medium">{t("taskLlmResponseFormatLabel")}</span>
        <select
          className="bg-background w-full rounded-md border px-2 py-1 text-sm disabled:opacity-50"
          disabled={disabled || role.inheritParameters}
          value={responseFormat}
          onChange={(event) => {
            const next = event.target.value as TaskGenerationParameters["responseFormat"];
            patchParams({ ...params, responseFormat: next === "text" ? "text" : next });
          }}
        >
          <option value="text">{t("taskLlmResponseFormatText")}</option>
          <option value="json_object">{t("taskLlmResponseFormatJsonObject")}</option>
          <option value="json_schema" disabled>
            {t("taskLlmResponseFormatJsonSchema")}
          </option>
        </select>
      </label>
      <BoundedNumberField
        label={t("taskLlmTimeoutLabel")}
        testId={`task-hp-timeout-${role.roleId}`}
        value={params.timeoutSeconds}
        min={1}
        max={600}
        step="1"
        disabled={disabled || role.inheritParameters}
        onCommit={(timeoutSeconds) => patchParams({ ...params, timeoutSeconds })}
      />
      <label className="min-w-0 w-full space-y-1 text-xs [flex-basis:100%]" data-testid={`task-hp-stop-${role.roleId}`}>
        <span className="text-muted-foreground font-medium">{t("taskLlmStopLabel")}</span>
        <textarea
          className="bg-background min-h-[4rem] w-full rounded-md border px-2 py-1 text-sm disabled:opacity-50"
          placeholder={t("taskLlmStopPlaceholder")}
          disabled={disabled || role.inheritParameters}
          value={formatStopSequencesText(params.stopSequences)}
          onChange={(event) => {
            const stopSequences = parseStopSequencesText(event.target.value);
            patchParams({ ...params, stopSequences });
          }}
        />
      </label>
      <label className="min-w-0 w-full space-y-1 text-xs [flex-basis:100%]" data-testid={`task-hp-think-${role.roleId}`}>
        <span className="text-muted-foreground font-medium">{t("taskLlmThinkLabel")}</span>
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="checkbox"
            disabled={disabled || role.inheritParameters}
            checked={thinkEnabled}
            onChange={(event) =>
              patchParams({ ...params, think: event.target.checked ? true : false })
            }
          />
          <span className="text-sm">{thinkEnabled ? "true" : "false"}</span>
        </div>
        {thinkEnabled ? (
          <p className="text-amber-700 text-xs dark:text-amber-300">{t("taskLlmThinkWarning")}</p>
        ) : null}
      </label>
    </div>
  );
}

export function AdvancedTaskModelSettingsForm({
  configValues,
  onChange,
}: AdvancedTaskModelSettingsFormProps) {
  const t = useTranslations("Settings");
  const catalogQ = useTaskLlmCatalogQuery();
  const modelsQ = useMeSelectableLlmModels("CHAT");
  const [resetAllOpen, setResetAllOpen] = useState(false);

  const roles = useMemo(
    () => readTaskModelRolesFromConfig(configValues, catalogQ.data?.tasks ?? []),
    [configValues, catalogQ.data?.tasks],
  );

  function commitRoles(nextRoles: TaskModelRoleForm[]) {
    onChange(mergeTaskModelRolesIntoConfig(configValues, nextRoles));
  }

  function patchRole(roleId: string, patch: Partial<TaskModelRoleForm>) {
    commitRoles(roles.map((r) => (r.roleId === roleId ? { ...r, ...patch, hasOverride: true } : r)));
  }

  function handleResetRole(roleId: string) {
    const catalogTask = catalogQ.data?.tasks.find((task) => task.id === roleId);
    const current = roles.find((r) => r.roleId === roleId);
    if (!current) return;
    const nextRoles = roles.map((r) =>
      r.roleId === roleId ? resetRoleToDefaults(r, catalogTask) : r,
    );
    const cleaned = mergeTaskModelRolesIntoConfig(configValues, nextRoles);
    const overrides = { ...(cleaned as Record<string, unknown>) };
    const nested = overrides[TASK_LLM_OVERRIDES_KEY];
    if (nested && typeof nested === "object" && !Array.isArray(nested)) {
      const map = { ...(nested as Record<string, unknown>) };
      delete map[roleId];
      if (Object.keys(map).length === 0) delete overrides[TASK_LLM_OVERRIDES_KEY];
      else overrides[TASK_LLM_OVERRIDES_KEY] = map;
    }
    onChange(overrides);
  }

  function handleResetAll() {
    const next = { ...configValues };
    delete next[TASK_LLM_OVERRIDES_KEY];
    onChange(next);
    setResetAllOpen(false);
  }

  if (catalogQ.isLoading) {
    return <p className="text-muted-foreground text-sm">{t("configLoading")}</p>;
  }

  return (
    <div className="flex min-w-0 max-w-full flex-col gap-4" data-testid="advanced-task-model-settings">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h4 className="text-sm font-medium">{t("taskLlmSettingsTitle")}</h4>
          <p className="text-muted-foreground mt-1 break-words text-xs">{t("taskLlmSettingsDescription")}</p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="max-w-full shrink-0 whitespace-normal"
          data-testid="task-model-reset-all"
          onClick={() => setResetAllOpen(true)}
        >
          {t("taskLlmResetAll")}
        </Button>
      </div>

      {roles.map((role) => (
        <details
          key={role.roleId}
          className="min-w-0 max-w-full overflow-hidden rounded-md border bg-muted/20 p-3"
          data-testid={`task-llm-row-${role.roleId}`}
        >
          <summary className="min-w-0 cursor-pointer list-none text-sm font-medium">
            <span
              className="block truncate"
              title={formatRoleSummary(role)}
              data-testid={`task-role-summary-${role.roleId}`}
            >
              {formatRoleSummary(role)}
            </span>
          </summary>
          <div className="mt-3 min-w-0 space-y-4">
            <p className="text-muted-foreground break-words text-xs">
              {role.inheritModel ? t("taskLlmInheritsMainModel") : t("taskLlmCustomModel")}
              {role.inheritParameters ? ` · ${t("taskLlmInheritsMainParameters")}` : ""}
            </p>
            <div className="flex min-w-0 flex-wrap gap-3">
              <div className="flex min-w-[220px] flex-1 items-start gap-2 text-xs">
                <input
                  id={`inherit-model-${role.roleId}`}
                  type="checkbox"
                  className="mt-0.5 shrink-0"
                  checked={role.inheritModel}
                  onChange={(e) =>
                    patchRole(role.roleId, {
                      inheritModel: e.target.checked,
                      modelId: e.target.checked
                        ? catalogQ.data?.tasks.find((task) => task.id === role.roleId)?.defaultModelId ?? ""
                        : role.modelId,
                    })
                  }
                />
                <Label htmlFor={`inherit-model-${role.roleId}`} className="min-w-0 break-words leading-snug">
                  {t("taskLlmInheritModelLabel")}
                </Label>
              </div>
              <div className="flex min-w-[220px] flex-1 items-start gap-2 text-xs">
                <input
                  id={`inherit-params-${role.roleId}`}
                  type="checkbox"
                  className="mt-0.5 shrink-0"
                  checked={role.inheritParameters}
                  onChange={(e) => patchRole(role.roleId, { inheritParameters: e.target.checked })}
                />
                <Label htmlFor={`inherit-params-${role.roleId}`} className="min-w-0 break-words leading-snug">
                  {t("taskLlmInheritParametersLabel")}
                </Label>
              </div>
              <div className="flex w-full min-w-[220px] flex-[1_1_100%] flex-col gap-1">
                <Label htmlFor={`task-model-${role.roleId}`}>{t("taskLlmModelLabel")}</Label>
                <select
                  id={`task-model-${role.roleId}`}
                  data-testid={`task-llm-model-select-${role.roleId}`}
                  className="border-input bg-background h-10 w-full min-w-0 rounded-md border px-3 py-2 text-sm"
                  value={role.modelId}
                  disabled={modelsQ.isLoading || role.inheritModel}
                  onChange={(e) =>
                    patchRole(role.roleId, { modelId: e.target.value, inheritModel: false })
                  }
                >
                  {(modelsQ.data?.models ?? []).map((m) => (
                    <option key={m.modelName} value={m.modelName} disabled={!m.selectable}>
                      {m.displayName?.trim() ? m.displayName : m.modelName}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <RoleParameterFields role={role} onPatch={(patch) => patchRole(role.roleId, patch)} t={t} />
            <Button
              type="button"
              variant="outline"
              size="sm"
              data-testid={`task-model-reset-role-${role.roleId}`}
              onClick={() => handleResetRole(role.roleId)}
            >
              {t("taskLlmResetRole")}
            </Button>
          </div>
        </details>
      ))}

      <Dialog open={resetAllOpen} onOpenChange={setResetAllOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("taskLlmResetAllTitle")}</DialogTitle>
            <DialogDescription>{t("taskLlmResetConfirm")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setResetAllOpen(false)}>
              {t("cancel")}
            </Button>
            <Button type="button" onClick={handleResetAll} data-testid="task-model-reset-all-confirm">
              {t("taskLlmResetAll")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
