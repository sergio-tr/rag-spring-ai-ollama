"use client";

import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ReactElement } from "react";
import { Controller, type UseFormReturn } from "react-hook-form";

/** Single-column editable fields from `/config/schema` (shared by presets + RAG config forms). */
export function ConfigSchemaFieldRows(props: Readonly<{
  fields: ConfigSchemaField[];
  form: UseFormReturn<ConfigFormValues>;
  labelFor: (fieldKey: string) => string;
  inputIdPrefix: string;
}>): ReactElement {
  const { fields, form, labelFor, inputIdPrefix } = props;

  return (
    <>
      {fields
        .filter((f) => f.userEditable)
        .map((f) => (
          <div key={f.key} className="flex flex-col gap-2">
            <Label htmlFor={`${inputIdPrefix}-${f.key}`}>{labelFor(f.key)}</Label>
            {f.type === "boolean" ? (
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
            ) : (
              <Input
                id={`${inputIdPrefix}-${f.key}`}
                type={f.type === "integer" || f.type === "number" ? "number" : "text"}
                step={f.type === "integer" ? "1" : undefined}
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
