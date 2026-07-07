"use client";

import { useTranslations } from "next-intl";
import type { MeEffectiveEmbeddingDefaultsResponse, MeEffectiveLlmDefaultsResponse } from "@/types/api";

type AssistantConfigurationEffectiveSummaryProps = Readonly<{
  llmDefaults?: MeEffectiveLlmDefaultsResponse;
  embeddingDefaults?: MeEffectiveEmbeddingDefaultsResponse;
  loading?: boolean;
}>;

export function AssistantConfigurationEffectiveSummary({
  llmDefaults,
  embeddingDefaults,
  loading,
}: AssistantConfigurationEffectiveSummaryProps) {
  const t = useTranslations("Settings");

  if (loading) {
    return (
      <p className="text-muted-foreground text-sm" data-testid="assistant-configuration-effective-loading">
        {t("configLoading")}
      </p>
    );
  }

  return (
    <section
      className="min-w-0 max-w-full overflow-hidden rounded-md border bg-muted/20 p-3 text-sm"
      data-testid="assistant-configuration-effective-summary"
    >
      <h3 className="font-medium">{t("assistantConfigurationEffectiveSummaryTitle")}</h3>
      <p className="text-muted-foreground mt-1 break-words text-xs">{t("assistantConfigurationEffectiveSummaryDescription")}</p>
      <dl className="mt-3 flex flex-wrap gap-2 text-xs">
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("assistantConfigurationEffectiveFinalAnswerModel")}</dt>
          <dd className="break-all font-mono">{llmDefaults?.chatModel ?? "-"}</dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("assistantConfigurationEffectiveClassifier")}</dt>
          <dd className="break-all font-mono">{llmDefaults?.classifierModelId ?? "-"}</dd>
        </div>
        <div className="min-w-[12rem] flex-1">
          <dt className="text-muted-foreground">{t("assistantConfigurationEffectiveEmbeddingModel")}</dt>
          <dd className="break-all font-mono">{embeddingDefaults?.embeddingModel ?? "-"}</dd>
        </div>
      </dl>
    </section>
  );
}
