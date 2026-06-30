"use client";

import type { ReactElement } from "react";
import { useTranslations } from "next-intl";
import {
  type LlmProviderKind,
  appliedModelParameters,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import { readParameterValue } from "@/features/settings/lib/llm-additional-parameters";

type EffectiveModelParametersPreviewProps = Readonly<{
  provider: LlmProviderKind | null;
  config: Record<string, unknown> | undefined;
}>;

/** Product-facing summary of model parameters that are actually applied for the provider. */
export function EffectiveModelParametersPreview({
  provider,
  config,
}: EffectiveModelParametersPreviewProps): ReactElement {
  const t = useTranslations("Settings");
  const applied = appliedModelParameters(provider);

  return (
    <div
      className="rounded-md border bg-muted/20 px-3 py-2 text-xs"
      data-testid="effective-model-parameters-preview"
    >
      <p className="mb-2 font-medium">{t("modelParamsEffectivePreviewTitle")}</p>
      <ul className="space-y-1">
        {applied.map((def) => {
          const value = readParameterValue(config, def.storage, def.configKey);
          const display =
            value === undefined || value === null || value === ""
              ? t("modelParamsEffectiveDefault")
              : String(value);
          return (
            <li key={def.id} className="flex justify-between gap-3" data-testid={`model-param-effective-${def.id}`}>
              <span className="text-muted-foreground">{t(def.labelKey as never)}</span>
              <span className="font-mono">{display}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
