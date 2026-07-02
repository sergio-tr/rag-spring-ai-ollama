"use client";

import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ReactElement } from "react";
import { Controller, type UseFormReturn } from "react-hook-form";

export type ConfigModelOption = Readonly<{
  value: string;
  label: string;
  disabled?: boolean;
}>;

/** Single-column editable fields from `/config/schema` (shared by presets + RAG config forms). */
export function ConfigSchemaFieldRows(props: Readonly<{
  fields: ConfigSchemaField[];
  form: UseFormReturn<ConfigFormValues>;
  labelFor: (fieldKey: string) => string;
  inputIdPrefix: string;
  llmModelOptions?: readonly ConfigModelOption[];
  embeddingModelOptions?: readonly ConfigModelOption[];
  effectiveProviderLabel?: string | null;
}>): ReactElement {
  const {
    fields,
    form,
    labelFor,
    inputIdPrefix,
    llmModelOptions = [],
    embeddingModelOptions = [],
    effectiveProviderLabel,
  } = props;

  return (
    <>
      {effectiveProviderLabel ? (
        <div className="flex flex-col gap-1" data-testid="config-effective-provider">
          <span className="text-muted-foreground text-xs font-medium">{labelFor("provider")}</span>
          <output className="text-sm">{effectiveProviderLabel}</output>
        </div>
      ) : null}
      {fields.map((f) => (
        <div key={f.key} className="flex min-w-0 max-w-full flex-col gap-2">
          {labelFor(f.key) ? <Label htmlFor={`${inputIdPrefix}-${f.key}`}>{labelFor(f.key)}</Label> : null}
          {f.key === "llmModel" && llmModelOptions.length > 0 ? (
            <Controller
              name={f.key}
              control={form.control}
              render={({ field }) => (
                <select
                  id={`${inputIdPrefix}-${f.key}`}
                  data-testid="config-llm-model-select"
                  className="border-input bg-background h-10 w-full rounded-md border px-3 py-2 text-sm"
                  value={typeof field.value === "string" ? field.value : ""}
                  onChange={(event) => field.onChange(event.target.value || undefined)}
                >
                  <option value="">{labelFor("llmModelDefaultOption")}</option>
                  {llmModelOptions.map((option) => (
                    <option key={option.value} value={option.value} disabled={option.disabled}>
                      {option.label}
                    </option>
                  ))}
                </select>
              )}
            />
          ) : f.key === "embeddingModel" && embeddingModelOptions.length > 0 ? (
            <Controller
              name={f.key}
              control={form.control}
              render={({ field }) => (
                <select
                  id={`${inputIdPrefix}-${f.key}`}
                  data-testid="config-embedding-model-select"
                  className="border-input bg-background h-10 w-full rounded-md border px-3 py-2 text-sm"
                  value={typeof field.value === "string" ? field.value : ""}
                  onChange={(event) => field.onChange(event.target.value || undefined)}
                >
                  <option value="">{labelFor("embeddingModelDefaultOption")}</option>
                  {embeddingModelOptions.map((option) => (
                    <option key={option.value} value={option.value} disabled={option.disabled}>
                      {option.label}
                    </option>
                  ))}
                </select>
              )}
            />
          ) : f.type === "boolean" ? (
            <Controller
              name={f.key}
              control={form.control}
              render={({ field }) => (
                <input
                  id={`${inputIdPrefix}-${f.key}`}
                  type="checkbox"
                  className="size-4"
                  checked={Boolean(field.value)}
                  onChange={(e) => field.onChange(e.target.checked)}
                />
              )}
            />
          ) : f.type === "text" ? (
            <textarea
              id={`${inputIdPrefix}-${f.key}`}
              data-testid={`config-field-${f.key}`}
              className="border-input bg-background min-h-28 w-full rounded-md border px-3 py-2 text-sm"
              maxLength={f.max ?? undefined}
              {...form.register(f.key, {
                setValueAs: (v) => {
                  if (v === "" || v === null || v === undefined) return undefined;
                  return String(v);
                },
              })}
            />
          ) : (
            <Input
              id={`${inputIdPrefix}-${f.key}`}
              type={f.type === "integer" || f.type === "number" ? "number" : "text"}
              step={
                f.type === "integer"
                  ? "1"
                  : f.type === "number"
                    ? f.key === "similarityThreshold"
                      ? "0.01"
                      : "any"
                    : undefined
              }
              min={f.min === undefined || f.min === null ? undefined : String(f.min)}
              max={f.max === undefined || f.max === null ? undefined : String(f.max)}
              {...form.register(f.key, {
                setValueAs: (v) => {
                  if (v === "" || v === null || v === undefined) return undefined;
                  if (f.type === "integer" || f.type === "number") {
                    const n = Number(v);
                    return Number.isNaN(n) ? undefined : n;
                  }
                  return v;
                },
              })}
            />
          )}
        </div>
      ))}
    </>
  );
}
