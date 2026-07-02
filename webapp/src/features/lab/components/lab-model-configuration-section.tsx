"use client";

import { Label } from "@/components/ui/label";
import { useTranslations } from "next-intl";

export type LabModelConfigurationSectionProps = {
  sectionKey: string;
  disabled?: boolean;
  embeddingModelId: string;
  primaryLlmModelId: string;
  secondaryLlmModelId?: string;
  embeddingModelIds: readonly string[];
  chatModelIds: readonly string[];
  onEmbeddingChange: (modelId: string) => void;
  onPrimaryLlmChange: (modelId: string) => void;
  onSecondaryLlmChange: (modelId: string | undefined) => void;
  selectedEmbeddingLabel?: string;
};

function ModelSelect({
  id,
  testId,
  label,
  value,
  options,
  disabled,
  placeholder,
  onChange,
}: {
  id: string;
  testId: string;
  label: string;
  value: string;
  options: readonly string[];
  disabled?: boolean;
  placeholder?: string;
  onChange: (next: string) => void;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        data-testid={testId}
        className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
        value={value}
        disabled={disabled || options.length === 0}
        onChange={(event) => onChange(event.target.value)}
      >
        {placeholder ? (
          <option value="">{placeholder}</option>
        ) : null}
        {options.map((modelId) => (
          <option key={modelId} value={modelId}>
            {modelId}
          </option>
        ))}
      </select>
    </div>
  );
}

export function LabModelConfigurationSection({
  sectionKey,
  disabled = false,
  embeddingModelId,
  primaryLlmModelId,
  secondaryLlmModelId,
  embeddingModelIds,
  chatModelIds,
  onEmbeddingChange,
  onPrimaryLlmChange,
  onSecondaryLlmChange,
  selectedEmbeddingLabel,
}: LabModelConfigurationSectionProps) {
  const t = useTranslations("Lab");

  return (
    <div
      className="space-y-3 rounded-md border bg-muted/20 p-3"
      data-testid="lab-model-configuration-section"
    >
      <Label className="text-sm">{t("benchmarkModelConfigurationTitle")}</Label>
      <p className="text-muted-foreground text-[11px]">{t("benchmarkModelConfigurationHint")}</p>

      <div className="grid gap-3 md:grid-cols-2">
        <ModelSelect
          id={`lab-benchmark-embedding-model-${sectionKey}`}
          testId="lab-benchmark-embedding-model"
          label={t("benchmarkColEmbeddingModel")}
          value={embeddingModelId}
          options={embeddingModelIds}
          disabled={disabled}
          onChange={onEmbeddingChange}
        />
        <ModelSelect
          id={`lab-benchmark-llm-model-${sectionKey}`}
          testId="lab-benchmark-llm-model"
          label={t("benchmarkPrimaryAnswerModel")}
          value={primaryLlmModelId}
          options={chatModelIds}
          disabled={disabled}
          onChange={onPrimaryLlmChange}
        />
        <ModelSelect
          id={`lab-benchmark-secondary-llm-model-${sectionKey}`}
          testId="lab-benchmark-secondary-llm-model"
          label={t("benchmarkSecondarySupportModel")}
          value={secondaryLlmModelId ?? ""}
          options={chatModelIds.filter((id) => id !== primaryLlmModelId)}
          disabled={disabled}
          placeholder={t("benchmarkSecondarySupportModelNone")}
          onChange={(next) => onSecondaryLlmChange(next.trim() || undefined)}
        />
      </div>

      {selectedEmbeddingLabel ? (
        <p className="text-muted-foreground text-xs" data-testid="lab-rag-selected-embedding-summary">
          {t("benchmarkRagSelectedEmbeddingSummary", { model: selectedEmbeddingLabel })}
        </p>
      ) : null}
    </div>
  );
}
