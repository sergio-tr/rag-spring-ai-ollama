"use client";

import { Label } from "@/components/ui/label";
import type { LabBenchmarkRuntimeParameters } from "@/features/lab/lib/lab-evaluation-draft";
import {
  buildBenchmarkRuntimeParametersPayload,
  clampNumber,
  formatStopSequencesText,
  parseStopSequencesText,
} from "@/features/lab/lib/lab-generation-hyperparameters";
import type { LabResponseFormat } from "@/features/lab/lib/lab-generation-hyperparameters";
import { useTranslations } from "next-intl";

export type LabGenerationParametersSectionProps = {
  value: LabBenchmarkRuntimeParameters;
  onChange: (next: LabBenchmarkRuntimeParameters) => void;
  disabled?: boolean;
};

export { buildBenchmarkRuntimeParametersPayload };

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
    <label className="space-y-1 text-xs" data-testid={testId}>
      <span className="text-muted-foreground font-medium">{label}</span>
      <input
        type="number"
        className="bg-background w-full rounded-md border px-2 py-1 font-mono disabled:opacity-50"
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

/** LLM generation parameters for RAG / LLM evaluation runs (separate from embedding options). */
export function LabGenerationParametersSection({
  value,
  onChange,
  disabled = false,
}: LabGenerationParametersSectionProps) {
  const t = useTranslations("Lab");
  const thinkEnabled = value.think === true;
  const responseFormat: LabResponseFormat = value.responseFormat ?? "text";

  return (
    <div className="space-y-3 rounded-md border bg-muted/20 p-3" data-testid="lab-generation-parameters-section">
      <Label className="text-sm">{t("benchmarkGenerationParametersTitle")}</Label>
      <p className="text-muted-foreground text-[11px]">{t("benchmarkGenerationParametersHint")}</p>
      <div className="grid gap-3 md:grid-cols-2">
        <BoundedNumberField
          label={t("benchmarkHyperparameterTemperature")}
          testId="lab-hp-temperature"
          value={value.temperature}
          min={0}
          max={2}
          step="0.1"
          disabled={disabled}
          onCommit={(temperature) => onChange({ ...value, temperature })}
        />
        <BoundedNumberField
          label={t("benchmarkHyperparameterTopP")}
          testId="lab-hp-top-p"
          value={value.topP}
          min={0}
          max={1}
          step="0.01"
          disabled={disabled}
          onCommit={(topP) => onChange({ ...value, topP })}
        />
        <label className="space-y-1 text-xs" data-testid="lab-hp-seed">
          <span className="text-muted-foreground font-medium">{t("benchmarkHyperparameterSeed")}</span>
          <input
            type="number"
            className="bg-background w-full rounded-md border px-2 py-1 font-mono disabled:opacity-50"
            step="1"
            disabled={disabled}
            value={typeof value.seed === "number" && Number.isFinite(value.seed) ? String(value.seed) : ""}
            onChange={(event) => {
              const text = event.target.value.trim();
              const next = { ...value };
              if (!text) {
                delete next.seed;
              } else {
                const parsed = Number(text);
                if (Number.isFinite(parsed)) next.seed = Math.trunc(parsed);
              }
              onChange(next);
            }}
          />
        </label>
        <BoundedNumberField
          label={t("benchmarkHyperparameterMaxTokens")}
          testId="lab-hp-max-tokens"
          value={value.maxTokens}
          min={1}
          max={1_000_000}
          step="1"
          disabled={disabled}
          onCommit={(maxTokens) => onChange({ ...value, maxTokens })}
        />
        <BoundedNumberField
          label={t("benchmarkHyperparameterPresencePenalty")}
          testId="lab-hp-presence-penalty"
          value={value.presencePenalty}
          min={-2}
          max={2}
          step="0.1"
          disabled={disabled}
          onCommit={(presencePenalty) => onChange({ ...value, presencePenalty })}
        />
        <BoundedNumberField
          label={t("benchmarkHyperparameterFrequencyPenalty")}
          testId="lab-hp-frequency-penalty"
          value={value.frequencyPenalty}
          min={-2}
          max={2}
          step="0.1"
          disabled={disabled}
          onCommit={(frequencyPenalty) => onChange({ ...value, frequencyPenalty })}
        />
        <label className="space-y-1 text-xs" data-testid="lab-hp-response-format">
          <span className="text-muted-foreground font-medium">{t("benchmarkHyperparameterResponseFormat")}</span>
          <select
            className="bg-background w-full rounded-md border px-2 py-1 font-mono text-[11px] disabled:opacity-50"
            disabled={disabled}
            value={responseFormat}
            onChange={(event) => {
              const next = event.target.value as LabResponseFormat;
              onChange({
                ...value,
                responseFormat: next === "text" ? undefined : next,
              });
            }}
          >
            <option value="text">{t("benchmarkHyperparameterResponseFormatText")}</option>
            <option value="json_object">{t("benchmarkHyperparameterResponseFormatJsonObject")}</option>
            <option value="json_schema" disabled>
              {t("benchmarkHyperparameterResponseFormatJsonSchema")}
            </option>
          </select>
        </label>
        <label className="space-y-1 text-xs md:col-span-2" data-testid="lab-hp-stop">
          <span className="text-muted-foreground font-medium">{t("benchmarkHyperparameterStop")}</span>
          <textarea
            className="bg-background min-h-[4rem] w-full rounded-md border px-2 py-1 font-mono text-[11px] disabled:opacity-50"
            placeholder={t("benchmarkHyperparameterStopPlaceholder")}
            disabled={disabled}
            value={formatStopSequencesText(value.stop)}
            onChange={(event) => {
              const stop = parseStopSequencesText(event.target.value);
              const next = { ...value };
              if (stop.length === 0) {
                delete next.stop;
              } else {
                next.stop = stop;
              }
              onChange(next);
            }}
          />
        </label>
        <label className="space-y-1 text-xs md:col-span-2" data-testid="lab-hp-think">
          <span className="text-muted-foreground font-medium">{t("benchmarkHyperparameterThink")}</span>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              disabled={disabled}
              checked={thinkEnabled}
              onChange={(event) =>
                onChange({
                  ...value,
                  think: event.target.checked ? true : undefined,
                })
              }
            />
            <span className="font-mono text-[11px]">{thinkEnabled ? "true" : "false"}</span>
          </div>
          {thinkEnabled ? (
            <p className="text-amber-700 text-[11px] dark:text-amber-300">{t("benchmarkHyperparameterThinkWarning")}</p>
          ) : null}
        </label>
      </div>
    </div>
  );
}
