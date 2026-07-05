"use client";

import { useTranslations } from "next-intl";
import { useClassifierModelsQuery } from "@/features/lab/hooks/use-classifier-registry";
import { Label } from "@/components/ui/label";

type AssistantConfigurationClassifierSectionProps = Readonly<{
  value: string;
  onChange: (classifierModelId: string) => void;
  effectiveClassifierModelId?: string | null;
}>;

export function AssistantConfigurationClassifierSection({
  value,
  onChange,
  effectiveClassifierModelId,
}: AssistantConfigurationClassifierSectionProps) {
  const t = useTranslations("Settings");
  const classifierModelsQuery = useClassifierModelsQuery(true);

  const selected = value.trim();
  const effective = effectiveClassifierModelId?.trim() ?? "";

  return (
    <section className="flex min-w-0 max-w-full flex-col gap-3" data-testid="assistant-configuration-classifier-section">
      <div className="min-w-0">
        <h3 className="text-sm font-medium">{t("assistantConfigurationClassifierTitle")}</h3>
        <p className="text-muted-foreground mt-1 break-words text-xs">{t("assistantConfigurationClassifierDescription")}</p>
      </div>
      <div className="flex min-w-0 flex-col gap-1">
        <Label htmlFor="assistant-classifier-model" className="text-xs">
          {t("assistantConfigurationClassifierLabel")}
        </Label>
        <select
          id="assistant-classifier-model"
          data-testid="assistant-classifier-model-select"
          className="border-input bg-background h-9 w-full min-w-0 rounded-md border px-2 text-sm"
          value={selected}
          onChange={(e) => onChange(e.target.value)}
          disabled={classifierModelsQuery.isLoading || classifierModelsQuery.isError}
        >
          <option value="">{t("assistantConfigurationClassifierSystemDefault")}</option>
          {(classifierModelsQuery.data ?? []).map((m) => (
            <option key={m.id} value={m.inferenceTag}>
              {m.name}
              {m.inferenceTag === "default" ? ` (${t("assistantConfigurationClassifierDefaultTag")})` : ""}
            </option>
          ))}
        </select>
        {classifierModelsQuery.isError ? (
          <p className="text-destructive text-xs" role="alert">
            {t("assistantConfigurationClassifierLoadError")}
          </p>
        ) : null}
        {effective && effective !== selected ? (
          <p className="text-muted-foreground break-words text-xs" data-testid="assistant-classifier-effective-hint">
            {t("assistantConfigurationClassifierEffectiveHint", { model: effective })}
          </p>
        ) : null}
      </div>
    </section>
  );
}
