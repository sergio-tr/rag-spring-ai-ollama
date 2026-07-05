"use client";

import type { ReactElement } from "react";
import { useTranslations } from "next-intl";
import type { UseFormReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import {
  isAdditionalParameterInherited,
  readEffectiveLlmParameter,
} from "@/features/settings/lib/effective-config-form-values";
import { readTemperature } from "@/features/settings/lib/llm-additional-parameters";
import {
  LLM_TEMPERATURE_KEY,
  type LlmProviderKind,
  type ModelParameterDef,
  appliedModelParameters,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import type { MeEffectiveLlmDefaultsResponse } from "@/types/api";

type ProviderAwareModelParametersProps = Readonly<{
  provider: LlmProviderKind | null;
  form: UseFormReturn<ConfigFormValues>;
  additionalParameters: Record<string, unknown>;
  onAdditionalParameterChange: (key: string, value: number | boolean | undefined) => void;
  effectiveDefaults?: MeEffectiveLlmDefaultsResponse;
  config?: Record<string, unknown>;
}>;

function InheritedHint({ show }: Readonly<{ show: boolean }>): ReactElement | null {
  const t = useTranslations("Settings");
  if (!show) return null;
  return (
    <p className="text-muted-foreground text-[11px]" data-testid="model-param-inherited-hint">
      {t("modelParamInheritedFromSystemDefault")}
    </p>
  );
}

function formatDisplayValue(value: unknown, t: (key: string) => string): string {
  if (value === undefined || value === null || value === "") {
    return t("modelParamsEffectiveNotConfigured");
  }
  return String(value);
}

function ParameterInput(props: Readonly<{
  def: ModelParameterDef;
  value: unknown;
  inherited: boolean;
  onChange: (value: number | undefined) => void;
}>): ReactElement {
  const { def, value, inherited, onChange } = props;
  const t = useTranslations("Settings");
  const display =
    value === undefined || value === null || value === ""
      ? ""
      : String(value);

  return (
    <div className="flex min-w-[220px] flex-1 flex-col gap-2" data-testid={`model-param-field-${def.id}`}>
      <Label htmlFor={`model-param-${def.id}`}>{t(def.labelKey as never)}</Label>
      <Input
        id={`model-param-${def.id}`}
        type="number"
        min={def.min}
        max={def.max}
        step={def.type === "integer" ? 1 : 0.01}
        value={display}
        placeholder={formatDisplayValue(value, (key) => t(key as never))}
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
      <InheritedHint show={inherited} />
    </div>
  );
}

function BooleanParameterInput(props: Readonly<{
  def: ModelParameterDef;
  value: unknown;
  inherited: boolean;
  onChange: (value: boolean | undefined) => void;
}>): ReactElement {
  const { def, value, inherited, onChange } = props;
  const t = useTranslations("Settings");
  const checked = value === true;

  return (
    <div className="flex min-w-[220px] flex-1 flex-col gap-2" data-testid={`model-param-field-${def.id}`}>
      <Label>{t(def.labelKey as never)}</Label>
      <div className="flex items-center gap-2">
        <input
          id={`model-param-${def.id}`}
          type="checkbox"
          className="size-4"
          checked={checked}
          onChange={(event) => onChange(event.target.checked ? true : undefined)}
        />
        <span className="text-muted-foreground text-xs font-mono">
          {formatDisplayValue(value, (key) => t(key as never))}
        </span>
      </div>
      <InheritedHint show={inherited} />
    </div>
  );
}

/** Editable model parameters applied by the active provider's chat mapper. */
export function ProviderAwareModelParameters({
  provider,
  form,
  additionalParameters,
  onAdditionalParameterChange,
  effectiveDefaults,
  config,
}: ProviderAwareModelParametersProps): ReactElement | null {
  const applied = appliedModelParameters(provider);
  if (applied.length === 0) return null;

  const temperature = form.watch(LLM_TEMPERATURE_KEY as keyof ConfigFormValues);
  const effectiveTemperature = readEffectiveLlmParameter(
    config,
    effectiveDefaults,
    provider,
    "topLevel",
    LLM_TEMPERATURE_KEY,
  );
  const configuredTemperature = readTemperature(config);
  const displayTemperature = temperature ?? effectiveTemperature;
  const temperatureInherited =
    configuredTemperature === undefined && effectiveTemperature !== undefined && effectiveTemperature !== null;

  return (
      <div className="flex min-w-0 max-w-full flex-wrap gap-3" data-testid="provider-aware-model-parameters">
      {applied.map((def) => {
        if (def.storage === "topLevel" && def.configKey === LLM_TEMPERATURE_KEY) {
          return (
            <ParameterInput
              key={def.id}
              def={def}
              value={displayTemperature}
              inherited={temperatureInherited}
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
          const configured = additionalParameters[def.configKey];
          const effectiveValue = readEffectiveLlmParameter(
            config,
            effectiveDefaults,
            provider,
            "additional",
            def.configKey,
          );
          const displayValue = configured ?? effectiveValue;
          const inherited = isAdditionalParameterInherited(config, def.configKey);

          if (def.type === "boolean") {
            return (
              <BooleanParameterInput
                key={def.id}
                def={def}
                value={displayValue}
                inherited={inherited}
                onChange={(v) => onAdditionalParameterChange(def.configKey, v)}
              />
            );
          }

          return (
            <ParameterInput
              key={def.id}
              def={def}
              value={displayValue}
              inherited={inherited}
              onChange={(v) => onAdditionalParameterChange(def.configKey, v)}
            />
          );
        }
        return null;
      })}
    </div>
  );
}
