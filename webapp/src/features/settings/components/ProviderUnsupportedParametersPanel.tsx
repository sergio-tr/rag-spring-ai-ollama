"use client";

import type { ReactElement } from "react";
import { useTranslations } from "next-intl";
import {
  type LlmProviderKind,
  unsupportedModelParameters,
} from "@/features/settings/lib/provider-aware-llm-parameters";
import { readParameterValue } from "@/features/settings/lib/llm-additional-parameters";

type ProviderUnsupportedParametersPanelProps = Readonly<{
  provider: LlmProviderKind | null;
  config: Record<string, unknown> | undefined;
}>;

/** Read-only list of parameters not applied (or not editable) for the active provider. */
export function ProviderUnsupportedParametersPanel({
  provider,
  config,
}: ProviderUnsupportedParametersPanelProps): ReactElement | null {
  const t = useTranslations("Settings");
  const unsupported = unsupportedModelParameters(provider);
  if (unsupported.length === 0) return null;

  return (
    <div className="space-y-2" data-testid="provider-unsupported-model-parameters">
      <p className="text-muted-foreground text-xs">{t("modelParamsUnsupportedHint")}</p>
      <ul className="space-y-2 text-xs">
        {unsupported.map((def) => {
          const stored = readParameterValue(config, def.storage, def.configKey);
          const hasValue = stored !== undefined && stored !== null && stored !== "";
          return (
            <li
              key={def.id}
              className="flex flex-wrap items-baseline justify-between gap-2 rounded-md border border-dashed bg-muted/20 px-3 py-2"
              data-testid={`model-param-unsupported-${def.id}`}
            >
              <span className="font-medium">{t(def.labelKey as never)}</span>
              <span className="text-muted-foreground">
                {hasValue ? String(stored) : t("modelParamsUnsupportedNotConfigured")}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-muted-foreground text-[11px] leading-relaxed">{t("modelParamsUnsupportedFutureWork")}</p>
    </div>
  );
}
