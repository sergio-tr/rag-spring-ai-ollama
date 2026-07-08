"use client";

import { useTranslations } from "next-intl";
import { labelProjectConfigField } from "@/features/settings/lib/project-config-field-copy";
import { ADVANCED_TECHNICAL_DETAILS_TITLE } from "@/lib/product-provider-labels";

type PresetProfileCardProps = Readonly<{
  values: Record<string, unknown>;
  presetId: string;
}>;

function boolLabel(value: unknown): string {
  if (value === true) return "On";
  if (value === false) return "Off";
  return "-";
}

export function PresetProfileCard({ values, presetId }: PresetProfileCardProps) {
  const t = useTranslations("Settings");

  const capabilityKeys = [
    "toolsEnabled",
    "metadataEnabled",
    "reasoningEnabled",
    "rankerEnabled",
    "functionCallingEnabled",
  ];
  const retrievalKeys = ["topK", "similarityThreshold", "expansionEnabled", "nerEnabled", "useRetrieval"];
  const memoryKeys = ["memoryEnabled", "useAdvisor"];
  const clarificationKeys = ["clarificationEnabled"];
  const qualityKeys = ["judgeEnabled", "postRetrievalEnabled"];

  function row(key: string) {
    if (!(key in values)) return null;
    return (
      <div key={key} className="flex justify-between gap-4 text-xs">
        <span className="text-muted-foreground">{labelProjectConfigField(key, (k) => t(k as never))}</span>
        <span className="font-medium">{String(values[key])}</span>
      </div>
    );
  }

  function boolSection(title: string, keys: string[]) {
    const rows = keys.filter((k) => k in values);
    if (rows.length === 0) return null;
    return (
      <div className="flex flex-col gap-1">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</h4>
        {rows.map((k) => (
          <div key={k} className="flex justify-between gap-4 text-xs">
            <span className="text-muted-foreground">{labelProjectConfigField(k, (l) => t(l as never))}</span>
            <span>{boolLabel(values[k])}</span>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="mt-2 flex flex-col gap-3" data-testid={`preset-profile-card-${presetId}`}>
      {"llmModel" in values || "embeddingModel" in values ? (
        <div className="flex flex-col gap-1">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t("settingsSectionModelConfiguration")}
          </h4>
          {row("llmModel")}
          {row("embeddingModel")}
        </div>
      ) : null}
      {boolSection(t("presetProfileCapabilities"), capabilityKeys)}
      {boolSection(t("presetProfileRetrieval"), retrievalKeys)}
      {boolSection(t("presetProfileMemory"), memoryKeys)}
      {boolSection(t("presetProfileClarification"), clarificationKeys)}
      {boolSection(t("presetProfileAnswerQuality"), qualityKeys)}
      <details className="text-xs">
        <summary className="cursor-pointer font-medium">{ADVANCED_TECHNICAL_DETAILS_TITLE}</summary>
        <pre className="mt-2 max-h-40 overflow-auto rounded border bg-muted p-2 font-mono text-[10px]">
          {JSON.stringify(values, null, 2)}
        </pre>
      </details>
    </div>
  );
}
