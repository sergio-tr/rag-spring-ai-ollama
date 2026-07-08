"use client";

import { Label } from "@/components/ui/label";
import type {
  LabBenchmarkRuntimeParameters,
  LabEvaluationDraftKind,
} from "@/features/lab/lib/lab-evaluation-draft";
import type { LabResponseFormat } from "@/features/lab/lib/lab-generation-hyperparameters";
import {
  buildBenchmarkRuntimeParametersPayload,
  clampNumber,
  formatStopSequencesText,
  parseStopSequencesText,
} from "@/features/lab/lib/lab-generation-hyperparameters";
import { EmbeddingEvaluatorOptionsForm } from "@/features/lab/components/EmbeddingEvaluatorOptionsForm";
import {
  buildEmbeddingBenchmarkRuntimeParametersPayload,
  type LabEmbeddingRuntimeParameters,
} from "@/features/lab/lib/lab-embedding-hyperparameters";
import type { LabEvaluationModelDto } from "@/types/api";
import { useTranslations } from "next-intl";

export type LabHyperparametersFormProps = {
  benchmarkKind: LabEvaluationDraftKind;
  value: LabBenchmarkRuntimeParameters;
  onChange: (next: LabBenchmarkRuntimeParameters) => void;
  embeddingModels?: LabEvaluationModelDto[];
};

export { buildBenchmarkRuntimeParametersPayload, buildEmbeddingBenchmarkRuntimeParametersPayload };

type BoundedNumberFieldProps = {
  label: string;
  testId: string;
  value: number | undefined;
  min: number;
  max: number;
  step: string;
  onCommit: (next: number | undefined) => void;
};

function BoundedNumberField({ label, testId, value, min, max, step, onCommit }: BoundedNumberFieldProps) {
  const display = typeof value === "number" && Number.isFinite(value) ? String(value) : "";
  return (
    <label className="space-y-1 text-xs" data-testid={testId}>
      <span className="text-muted-foreground font-medium">{label}</span>
      <input
        type="number"
        className="bg-background w-full rounded-md border px-2 py-1 font-mono"
        step={step}
        min={min}
        max={max}
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

function LabGenerationHyperparametersFields({
  value,
  onChange,
}: {
  value: LabBenchmarkRuntimeParameters;
  onChange: (next: LabBenchmarkRuntimeParameters) => void;
}) {
  const t = useTranslations("Lab");
  const thinkEnabled = value.think === true;
  const responseFormat: LabResponseFormat = value.responseFormat ?? "text";

  return (
    <>
      <BoundedNumberField
        label={t("benchmarkHyperparameterTemperature")}
        testId="lab-hp-temperature"
        value={value.temperature}
        min={0}
        max={2}
        step="0.1"
        onCommit={(temperature) => onChange({ ...value, temperature })}
      />
      <BoundedNumberField
        label={t("benchmarkHyperparameterTopP")}
        testId="lab-hp-top-p"
        value={value.topP}
        min={0}
        max={1}
        step="0.01"
        onCommit={(topP) => onChange({ ...value, topP })}
      />
      <label className="space-y-1 text-xs" data-testid="lab-hp-seed">
        <span className="text-muted-foreground font-medium">{t("benchmarkHyperparameterSeed")}</span>
        <input
          type="number"
          className="bg-background w-full rounded-md border px-2 py-1 font-mono"
          step="1"
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
        onCommit={(maxTokens) => onChange({ ...value, maxTokens })}
      />
      <BoundedNumberField
        label={t("benchmarkHyperparameterPresencePenalty")}
        testId="lab-hp-presence-penalty"
        value={value.presencePenalty}
        min={-2}
        max={2}
        step="0.1"
        onCommit={(presencePenalty) => onChange({ ...value, presencePenalty })}
      />
      <BoundedNumberField
        label={t("benchmarkHyperparameterFrequencyPenalty")}
        testId="lab-hp-frequency-penalty"
        value={value.frequencyPenalty}
        min={-2}
        max={2}
        step="0.1"
        onCommit={(frequencyPenalty) => onChange({ ...value, frequencyPenalty })}
      />
      <label className="space-y-1 text-xs" data-testid="lab-hp-response-format">
        <span className="text-muted-foreground font-medium">{t("benchmarkHyperparameterResponseFormat")}</span>
        <select
          className="bg-background w-full rounded-md border px-2 py-1 font-mono text-[11px]"
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
          className="bg-background min-h-[4rem] w-full rounded-md border px-2 py-1 font-mono text-[11px]"
          placeholder={t("benchmarkHyperparameterStopPlaceholder")}
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
    </>
  );
}

export function LabHyperparametersForm({
  benchmarkKind,
  value,
  onChange,
  embeddingModels = [],
}: LabHyperparametersFormProps) {
  const t = useTranslations("Lab");
  const showGenerationParameters = benchmarkKind === "LLM_JUDGE_QA";
  const showEmbeddingRetrievalParameters =
    benchmarkKind === "EMBEDDING_RETRIEVAL" || benchmarkKind === "RAG_PRESET_END_TO_END";
  const title = showEmbeddingRetrievalParameters
    ? benchmarkKind === "RAG_PRESET_END_TO_END"
      ? t("benchmarkEmbeddingRetrievalParametersTitle")
      : t("embeddingEvaluationParametersTitle")
    : t("benchmarkHyperparametersTitle");
  const hint = showEmbeddingRetrievalParameters
    ? benchmarkKind === "RAG_PRESET_END_TO_END"
      ? t("benchmarkEmbeddingRetrievalParametersHint")
      : t("embeddingEvaluationParametersHint")
    : t("benchmarkHyperparametersHint");

  return (
    <div className="space-y-3 rounded-md border bg-muted/20 p-3" data-testid="lab-hyperparameters-form">
      <Label className="text-sm">{title}</Label>
      <p className="text-muted-foreground text-[11px]">{hint}</p>

      {showGenerationParameters ? (
        <div className="grid gap-3 md:grid-cols-2">
          <LabGenerationHyperparametersFields value={value} onChange={onChange} />
        </div>
      ) : null}

      {benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
        <EmbeddingEvaluatorOptionsForm
          value={value as LabEmbeddingRuntimeParameters}
          onChange={onChange}
          selectedModels={embeddingModels}
        />
      ) : null}

      {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
        <EmbeddingEvaluatorOptionsForm
          value={value as LabEmbeddingRuntimeParameters}
          onChange={onChange}
          selectedModels={embeddingModels}
          showMaterializationStrategy={false}
        />
      ) : null}
    </div>
  );
}
