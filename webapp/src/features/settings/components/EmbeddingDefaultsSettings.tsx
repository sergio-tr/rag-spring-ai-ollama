"use client";

import type { ReactElement } from "react";
import { useTranslations } from "next-intl";
import type { UseFormReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import {
  isFieldInherited,
  readEffectiveEmbeddingField,
} from "@/features/settings/lib/effective-config-form-values";
import { useMeEffectiveEmbeddingDefaults } from "@/features/settings/hooks/use-me-effective-embedding-defaults";
import { useLabEvaluationModels } from "@/features/lab/hooks/use-lab-evaluation-models";

type EmbeddingDefaultsSettingsProps = Readonly<{
  form: UseFormReturn<ConfigFormValues>;
  config?: Record<string, unknown>;
}>;

function InheritedHint({ show }: Readonly<{ show: boolean }>): ReactElement | null {
  const t = useTranslations("Settings");
  if (!show) return null;
  return (
    <p className="text-muted-foreground text-[11px]" data-testid="embedding-default-inherited-hint">
      {t("modelParamInheritedFromSystemDefault")}
    </p>
  );
}

function formatFieldValue(value: unknown): string {
  if (value === undefined || value === null || value === "") {
    return "";
  }
  return String(value);
}

function notConfiguredLabel(t: (key: string) => string): string {
  return t("modelParamsEffectiveNotConfigured");
}

/** Settings section for embedding model defaults and hyperparameters. */
export function EmbeddingDefaultsSettings({ form, config }: EmbeddingDefaultsSettingsProps): ReactElement {
  const t = useTranslations("Settings");
  const effectiveQ = useMeEffectiveEmbeddingDefaults();
  const embeddingCatalog = useLabEvaluationModels("EMBEDDING");
  const effective = effectiveQ.data;

  const selectedEmbeddingModel = String(
    form.watch("embeddingModel" as keyof ConfigFormValues) ??
      readEffectiveEmbeddingField(config, effective, "embeddingModel") ??
      "",
  );
  const selectedCatalog = (embeddingCatalog.data?.models ?? []).find((m) => m.modelName === selectedEmbeddingModel);
  const supportsDimensions = selectedCatalog?.supportsDimensions === true;
  const supportsEncodingFormat = selectedCatalog?.supportsEncodingFormat === true;
  const supportsNormalize = selectedCatalog?.supportsNormalize === true;
  const supportsTruncate = selectedCatalog?.supportsTruncate === true;

  const fields: Array<{
    key: keyof ConfigFormValues;
    label: string;
    type: "number" | "select" | "checkbox";
    disabled?: boolean;
    disabledHint?: string;
    options?: string[];
  }> = [
    {
      key: "embeddingEncodingFormat" as keyof ConfigFormValues,
      label: t("embeddingDefaultsEncodingFormat"),
      type: "select",
      disabled: !supportsEncodingFormat,
      disabledHint: t("embeddingDefaultsUnsupportedByModel"),
      options: selectedCatalog?.supportedEncodingFormats ?? ["float", "base64"],
    },
    {
      key: "embeddingDimensions" as keyof ConfigFormValues,
      label: t("embeddingDefaultsDimensions"),
      type: "number",
      disabled: !supportsDimensions,
      disabledHint: t("embeddingDefaultsUnsupportedByModel"),
    },
    {
      key: "embeddingTimeoutSeconds" as keyof ConfigFormValues,
      label: t("embeddingDefaultsTimeoutSeconds"),
      type: "number",
    },
    {
      key: "topK" as keyof ConfigFormValues,
      label: t("embeddingDefaultsTopK"),
      type: "number",
    },
    {
      key: "similarityThreshold" as keyof ConfigFormValues,
      label: t("embeddingDefaultsSimilarityThreshold"),
      type: "number",
    },
    {
      key: "materializationStrategy" as keyof ConfigFormValues,
      label: t("embeddingDefaultsMaterializationStrategy"),
      type: "select",
      options: ["CHUNK_LEVEL", "DOCUMENT_LEVEL", "HYBRID"],
    },
    {
      key: "embeddingBatchSize" as keyof ConfigFormValues,
      label: t("embeddingDefaultsBatchSize"),
      type: "number",
    },
    {
      key: "embeddingMaxInputChars" as keyof ConfigFormValues,
      label: t("embeddingDefaultsMaxInputChars"),
      type: "number",
    },
    {
      key: "embeddingNormalize" as keyof ConfigFormValues,
      label: t("embeddingDefaultsNormalize"),
      type: "checkbox",
      disabled: !supportsNormalize,
      disabledHint: t("embeddingDefaultsUnsupportedByModel"),
    },
    {
      key: "embeddingTruncate" as keyof ConfigFormValues,
      label: t("embeddingDefaultsTruncate"),
      type: "select",
      disabled: !supportsTruncate,
      disabledHint: t("embeddingDefaultsUnsupportedByModel"),
      options: ["NONE", "START", "END"],
    },
  ];

  return (
    <div className="min-w-0 max-w-full space-y-3 rounded-md border bg-muted/20 p-3" data-testid="embedding-defaults-settings">
      <Label className="text-sm">{t("embeddingDefaultsTitle")}</Label>
      <p className="text-muted-foreground text-xs">{t("embeddingDefaultsHint")}</p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {fields.map((field) => {
          const configured = form.watch(field.key);
          const effectiveValue = readEffectiveEmbeddingField(config, effective, String(field.key));
          const displayValue = configured ?? effectiveValue;
          const inherited = isFieldInherited(config, String(field.key));

          if (field.type === "select") {
            const selectValue = formatFieldValue(displayValue);
            return (
              <div key={String(field.key)} className="space-y-1" data-testid={`embedding-default-${String(field.key)}`}>
                <Label htmlFor={`embedding-default-${String(field.key)}`}>{field.label}</Label>
                <select
                  id={`embedding-default-${String(field.key)}`}
                  className="bg-background w-full rounded-md border px-2 py-1 text-sm disabled:opacity-50"
                  disabled={field.disabled}
                  value={selectValue}
                  onChange={(event) => {
                    const raw = event.target.value.trim();
                    form.setValue(field.key, (raw || undefined) as never, {
                      shouldDirty: true,
                      shouldValidate: true,
                    });
                  }}
                >
                  {selectValue === "" ? (
                    <option value="">{notConfiguredLabel((key) => t(key as never))}</option>
                  ) : null}
                  {(field.options ?? []).map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
                {field.disabled ? (
                  <p className="text-muted-foreground text-[11px]">{field.disabledHint}</p>
                ) : (
                  <InheritedHint show={inherited} />
                )}
              </div>
            );
          }

          if (field.type === "checkbox") {
            const checked = displayValue === true;
            return (
              <div key={String(field.key)} className="space-y-1" data-testid={`embedding-default-${String(field.key)}`}>
                <Label>{field.label}</Label>
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    disabled={field.disabled}
                    checked={checked}
                    onChange={(event) =>
                      form.setValue(field.key, (event.target.checked ? true : undefined) as never, {
                        shouldDirty: true,
                        shouldValidate: true,
                      })
                    }
                  />
                  <span className="text-muted-foreground text-xs font-mono">
                    {checked ? "true" : displayValue === false ? "false" : notConfiguredLabel((key) => t(key as never))}
                  </span>
                </div>
                {field.disabled ? (
                  <p className="text-muted-foreground text-[11px]">{field.disabledHint}</p>
                ) : (
                  <InheritedHint show={inherited} />
                )}
              </div>
            );
          }

          return (
            <div key={String(field.key)} className="space-y-1" data-testid={`embedding-default-${String(field.key)}`}>
              <Label htmlFor={`embedding-default-${String(field.key)}`}>{field.label}</Label>
              <Input
                id={`embedding-default-${String(field.key)}`}
                type="number"
                disabled={field.disabled}
                value={formatFieldValue(displayValue)}
                placeholder={notConfiguredLabel((key) => t(key as never))}
                onChange={(event) => {
                  const raw = event.target.value.trim();
                  form.setValue(field.key, (raw ? Number(raw) : undefined) as never, {
                    shouldDirty: true,
                    shouldValidate: true,
                  });
                }}
              />
              {field.disabled ? (
                <p className="text-muted-foreground text-[11px]">{field.disabledHint}</p>
              ) : (
                <InheritedHint show={inherited} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
