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
    <div className="min-w-0 max-w-full space-y-2" data-testid="provider-unsupported-model-parameters">
      <p className="text-muted-foreground break-words text-xs">{t("modelParamsUnsupportedHint")}</p>
      <ul className="space-y-2 text-xs">
        {unsupported.map((def) => {
          const stored = readParameterValue(config, def.storage, def.configKey);
          const hasValue = stored !== undefined && stored !== null && stored !== "";
          return (
            <li
              key={def.id}
              className="flex min-w-0 flex-wrap items-baseline justify-between gap-2 rounded-md border border-dashed bg-muted/20 px-3 py-2"
              data-testid={`model-param-unsupported-${def.id}`}
            >
              <span className="min-w-0 break-words font-medium">{t(def.labelKey as never)}</span>
              <span className="text-muted-foreground min-w-0 shrink-0 break-all text-right">
                {hasValue ? String(stored) : t("modelParamsUnsupportedNotConfigured")}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-muted-foreground text-[11px] leading-relaxed break-words">{t("modelParamsUnsupportedFutureWork")}</p>
    </div>
  );
}
