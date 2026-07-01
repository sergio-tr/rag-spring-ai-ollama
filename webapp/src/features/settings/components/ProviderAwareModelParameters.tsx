"use client";

import type { ReactElement } from "react";
import { useTranslations } from "next-intl";
import type { UseFormReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import {
  LLM_TEMPERATURE_KEY,
  type LlmProviderKind,
  type ModelParameterDef,
  appliedModelParameters,
} from "@/features/settings/lib/provider-aware-llm-parameters";

type ProviderAwareModelParametersProps = Readonly<{
  provider: LlmProviderKind | null;
  form: UseFormReturn<ConfigFormValues>;
  additionalParameters: Record<string, unknown>;
  onAdditionalParameterChange: (key: string, value: number | undefined) => void;
}>;

function ParameterInput(props: Readonly<{
  def: ModelParameterDef;
  value: unknown;
  onChange: (value: number | undefined) => void;
}>): ReactElement {
  const { def, value, onChange } = props;
  const t = useTranslations("Settings");
  const display = value === undefined || value === null ? "" : String(value);

  return (
    <div className="flex flex-col gap-2" data-testid={`model-param-field-${def.id}`}>
      <Label htmlFor={`model-param-${def.id}`}>{t(def.labelKey as never)}</Label>
      <Input
        id={`model-param-${def.id}`}
        type="number"
        min={def.min}
        max={def.max}
        step={def.type === "integer" ? 1 : 0.01}
        value={display}
        onChange={(e) => {
          const raw = e.target.value.trim();
          if (!raw) {
            onChange(undefined);
            return;
          }
          const parsed = def.type === "integer" ? Number.parseInt(raw, 10) : Number.parseFloat(raw);
          onChange(Number.isFinite(parsed) ? parsed : undefined);
        }}
      />
    </div>
  );
}

/** Editable model parameters applied by the active provider's chat mapper. */
export function ProviderAwareModelParameters({
  provider,
  form,
  additionalParameters,
  onAdditionalParameterChange,
}: ProviderAwareModelParametersProps): ReactElement | null {
  const applied = appliedModelParameters(provider);
  if (applied.length === 0) return null;

  const temperature = form.watch(LLM_TEMPERATURE_KEY as keyof ConfigFormValues);

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2" data-testid="provider-aware-model-parameters">
      {applied.map((def) => {
        if (def.storage === "topLevel" && def.configKey === LLM_TEMPERATURE_KEY) {
          return (
            <ParameterInput
              key={def.id}
              def={def}
              value={temperature}
              onChange={(v) =>
                form.setValue(LLM_TEMPERATURE_KEY as keyof ConfigFormValues, v as never, {
                  shouldDirty: true,
                  shouldValidate: true,
                })
              }
            />
          );
        }
        if (def.storage === "additional") {
          return (
            <ParameterInput
              key={def.id}
              def={def}
              value={additionalParameters[def.configKey]}
              onChange={(v) => onAdditionalParameterChange(def.configKey, v)}
            />
          );
        }
        return null;
      })}
    </div>
  );
}
