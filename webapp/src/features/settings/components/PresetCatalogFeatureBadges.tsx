"use client";

import { useTranslations } from "next-intl";
import {
  deriveExperimentalPresetCatalogFeatures,
  deriveProductPresetCatalogFeatures,
  formatPresetCatalogFeatureLabelKey,
  type PresetCatalogFeatureState,
} from "@/features/settings/lib/preset-catalog-features";
import type { ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";
import { cn } from "@/lib/utils";

function FeatureBadge({
  label,
  enabled,
}: Readonly<{
  label: string;
  enabled: boolean;
}>) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-medium",
        enabled ? "bg-primary/10 text-primary" : "bg-muted/60 text-muted-foreground line-through opacity-70",
      )}
      data-enabled={enabled ? "true" : "false"}
    >
      {label}
    </span>
  );
}

function FeatureMatrix({
  features,
  testId,
}: Readonly<{
  features: PresetCatalogFeatureState[];
  testId: string;
}>) {
  const t = useTranslations("Settings");

  return (
    <div className="flex flex-wrap gap-1" data-testid={testId}>
      {features.map(({ key, enabled }) => (
        <FeatureBadge
          key={key}
          enabled={enabled}
          label={t(formatPresetCatalogFeatureLabelKey(key) as never)}
        />
      ))}
    </div>
  );
}

export function ProductPresetFeatureMatrix({
  preset,
}: Readonly<{ preset: Pick<RagPresetDto, "id" | "values"> }>) {
  const features = deriveProductPresetCatalogFeatures(preset);
  return <FeatureMatrix features={features} testId={`preset-feature-matrix-${preset.id}`} />;
}

export function ExperimentalPresetFeatureMatrix({
  preset,
}: Readonly<{ preset: ExperimentalPresetCatalogItemDto }>) {
  const features = deriveExperimentalPresetCatalogFeatures(preset);
  return (
    <FeatureMatrix
      features={features}
      testId={`preset-feature-matrix-${preset.code}`}
    />
  );
}
