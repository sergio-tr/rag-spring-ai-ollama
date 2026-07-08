"use client";

import {
  aggregateEmbeddingCapabilities,
  type LabEmbeddingRuntimeParameters,
} from "@/features/lab/lib/lab-embedding-hyperparameters";
import { clampNumber } from "@/features/lab/lib/lab-generation-hyperparameters";
import type { LabEvaluationModelDto } from "@/types/api";
import { useTranslations } from "next-intl";

export type EmbeddingEvaluatorOptionsFormProps = {
  value: LabEmbeddingRuntimeParameters;
  onChange: (next: LabEmbeddingRuntimeParameters) => void;
  selectedModels: LabEvaluationModelDto[];
  /** When false, materialization is derived from preset groups (RAG evaluation). */
  showMaterializationStrategy?: boolean;
};

function NumberField(props: Readonly<{
  label: string;
  testId: string;
  value: number | undefined;
  min: number;
  max: number;
  step: string;
  disabled?: boolean;
  onCommit: (next: number | undefined) => void;
}>) {
  const display = typeof props.value === "number" && Number.isFinite(props.value) ? String(props.value) : "";
  return (
    <label className="space-y-1 text-xs" data-testid={props.testId}>
      <span className="text-muted-foreground font-medium">{props.label}</span>
      <input
        type="number"
        className="bg-background w-full rounded-md border px-2 py-1 font-mono disabled:opacity-50"
        step={props.step}
        min={props.min}
        max={props.max}
        disabled={props.disabled}
        value={display}
        onChange={(event) => {
          const text = event.target.value.trim();
          if (!text) {
            props.onCommit(undefined);
            return;
          }
          const parsed = Number(text);
          if (!Number.isFinite(parsed)) return;
          props.onCommit(clampNumber(parsed, props.min, props.max));
        }}
      />
    </label>
  );
}

/** Shared embedding / retrieval / indexing options for embedding evaluator runs. */
export function EmbeddingEvaluatorOptionsForm({
  value,
  onChange,
  selectedModels,
  showMaterializationStrategy = true,
}: EmbeddingEvaluatorOptionsFormProps) {
  const t = useTranslations("Lab");
  const caps = aggregateEmbeddingCapabilities(selectedModels);

  return (
    <div className="space-y-3" data-testid="embedding-evaluator-options-form">
      <div className="grid gap-3 md:grid-cols-2">
        <label className="space-y-1 text-xs" data-testid="lab-emb-encoding-format">
          <span className="text-muted-foreground font-medium">{t("embeddingParamEncodingFormat")}</span>
          <select
            className="bg-background w-full rounded-md border px-2 py-1 font-mono text-[11px] disabled:opacity-50"
            disabled={!caps.supportsEncodingFormat}
            value={value.encodingFormat ?? ""}
            onChange={(event) => {
              const next = event.target.value;
              onChange({
                ...value,
                encodingFormat: next === "float" || next === "base64" ? next : undefined,
              });
            }}
          >
            <option value="">{t("embeddingParamUseEffectiveDefault")}</option>
            {caps.supportedEncodingFormats.map((format) => (
              <option key={format} value={format}>
                {format}
              </option>
            ))}
          </select>
        </label>

        <NumberField
          label={t("embeddingParamDimensions")}
          testId="lab-emb-dimensions"
          value={value.dimensions}
          min={1}
          max={8192}
          step="1"
          disabled={!caps.supportsDimensions}
          onCommit={(dimensions) => onChange({ ...value, dimensions })}
        />

        <NumberField
          label={t("embeddingParamTimeoutSeconds")}
          testId="lab-emb-timeout"
          value={value.timeoutSeconds}
          min={1}
          max={600}
          step="1"
          onCommit={(timeoutSeconds) => onChange({ ...value, timeoutSeconds })}
        />

        <NumberField
          label={t("benchmarkHyperparameterTopK")}
          testId="lab-hp-top-k"
          value={value.topK}
          min={1}
          max={10_000}
          step="1"
          onCommit={(topK) => onChange({ ...value, topK })}
        />

        <NumberField
          label={t("benchmarkHyperparameterSimilarityThreshold")}
          testId="lab-hp-similarity-threshold"
          value={value.similarityThreshold}
          min={0}
          max={1}
          step="0.01"
          onCommit={(similarityThreshold) => onChange({ ...value, similarityThreshold })}
        />

        {showMaterializationStrategy ? (
          <label className="space-y-1 text-xs" data-testid="lab-emb-materialization">
            <span className="text-muted-foreground font-medium">{t("materializationStrategyLabel")}</span>
            <select
              className="bg-background w-full rounded-md border px-2 py-1 font-mono text-[11px]"
              value={value.materializationStrategy ?? ""}
              onChange={(event) =>
                onChange({
                  ...value,
                  materializationStrategy: event.target.value.trim() || undefined,
                })
              }
            >
              <option value="">{t("embeddingParamUseEffectiveDefault")}</option>
              <option value="CHUNK_LEVEL">CHUNK_LEVEL</option>
              <option value="DOCUMENT_LEVEL">DOCUMENT_LEVEL</option>
              <option value="HYBRID">HYBRID</option>
            </select>
          </label>
        ) : null}

        <NumberField
          label={t("embeddingParamBatchSize")}
          testId="lab-emb-batch-size"
          value={value.batchSize}
          min={1}
          max={512}
          step="1"
          onCommit={(batchSize) => onChange({ ...value, batchSize })}
        />

        <NumberField
          label={t("embeddingParamMaxInputChars")}
          testId="lab-emb-max-input-chars"
          value={value.maxInputChars}
          min={64}
          max={32_768}
          step="1"
          onCommit={(maxInputChars) => onChange({ ...value, maxInputChars })}
        />

        <label className="space-y-1 text-xs md:col-span-2" data-testid="lab-emb-normalize">
          <span className="text-muted-foreground font-medium">{t("embeddingParamNormalize")}</span>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              disabled={!caps.supportsNormalize}
              checked={value.normalize === true}
              onChange={(event) =>
                onChange({
                  ...value,
                  normalize: event.target.checked ? true : undefined,
                })
              }
            />
            <span className="font-mono text-[11px]">{value.normalize === true ? "true" : "false"}</span>
          </div>
        </label>
      </div>
      {caps.defaultDimensions != null ? (
        <p className="text-muted-foreground text-[11px]" data-testid="lab-emb-capability-hint">
          {t("embeddingParamDefaultDimensionsHint", { dimensions: caps.defaultDimensions })}
        </p>
      ) : null}
    </div>
  );
}
