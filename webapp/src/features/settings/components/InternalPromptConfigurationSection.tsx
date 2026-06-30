"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { usePromptCatalogQuery } from "@/features/settings/hooks/use-prompt-catalog";
import {
  effectivePromptContent,
  mergePromptOverrides,
  PROMPT_OVERRIDES_KEY,
  readPromptOverrides,
} from "@/features/settings/lib/prompt-overrides";
import { ADVANCED_TECHNICAL_DETAILS_TITLE } from "@/lib/product-provider-labels";

type InternalPromptConfigurationSectionProps = Readonly<{
  configValues: Record<string, unknown>;
  onChange: (next: Record<string, unknown>) => void;
}>;

export function InternalPromptConfigurationSection({
  configValues,
  onChange,
}: InternalPromptConfigurationSectionProps) {
  const t = useTranslations("Settings");
  const catalogQ = usePromptCatalogQuery();
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);

  const overrides = useMemo(() => readPromptOverrides(configValues), [configValues]);

  const editableGroups = useMemo(
    () => (catalogQ.data?.groups ?? []).filter((g) => g.id !== "system_instructions" && g.runtimeEditable !== false),
    [catalogQ.data?.groups],
  );

  const catalogOnlyGroups = useMemo(
    () => (catalogQ.data?.groups ?? []).filter((g) => g.runtimeEditable === false && g.id !== "system_instructions"),
    [catalogQ.data?.groups],
  );

  function setGroupContent(groupId: string, content: string) {
    const nextOverrides = { ...overrides, [groupId]: content };
    onChange(mergePromptOverrides(configValues, nextOverrides));
  }

  if (catalogQ.isLoading) {
    return <p className="text-muted-foreground text-sm">{t("configLoading")}</p>;
  }

  if (catalogQ.isError) {
    return (
      <p className="text-destructive text-sm" role="alert">
        {t("promptCatalogLoadError")}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3" id="internal-prompt-configuration" data-testid="internal-prompt-configuration">
      <p className="text-muted-foreground text-xs">{t("internalPromptsSectionDescription")}</p>
      {editableGroups.map((group) => {
        const content = effectivePromptContent(group, overrides);
        const isCustom = Boolean(overrides[group.id]?.trim());
        const expanded = openGroupId === group.id;
        return (
          <details
            key={group.id}
            open={expanded}
            className="rounded-md border bg-muted/20 p-3 text-sm"
            data-testid={`internal-prompt-group-${group.id}`}
            onToggle={(e) => {
              const el = e.currentTarget;
              setOpenGroupId(el.open ? group.id : null);
            }}
          >
            <summary className="cursor-pointer font-medium">{group.componentLabel}</summary>
            <div className="mt-3 flex flex-col gap-2">
              <p className="text-muted-foreground text-xs">{group.description}</p>
              {group.requiredVariables.length > 0 ? (
                <div className="flex flex-wrap gap-1">
                  {group.requiredVariables.map((v) => (
                    <span
                      key={v}
                      className="bg-background rounded border px-1.5 py-0.5 font-mono text-[10px]"
                    >
                      {v}
                    </span>
                  ))}
                </div>
              ) : null}
              <Label htmlFor={`prompt-${group.id}`} className="sr-only">
                {group.componentLabel}
              </Label>
              <Textarea
                id={`prompt-${group.id}`}
                value={content}
                rows={8}
                className="font-mono text-xs"
                onChange={(e) => setGroupContent(group.id, e.target.value)}
              />
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  data-testid={`internal-prompt-reset-${group.id}`}
                  onClick={() => {
                    const nextOverrides = { ...overrides };
                    delete nextOverrides[group.id];
                    onChange(
                      mergePromptOverrides(configValues, {
                        ...nextOverrides,
                        [group.id]: group.defaultContent,
                      }),
                    );
                  }}
                >
                  {t("instructionsResetToDefault")}
                </Button>
                {isCustom ? (
                  <span className="text-muted-foreground self-center text-xs">{t("internalPromptCustomized")}</span>
                ) : null}
              </div>
            </div>
          </details>
        );
      })}
      <details className="rounded-md border bg-muted/10 p-3 text-xs">
        <summary className="cursor-pointer font-medium">{ADVANCED_TECHNICAL_DETAILS_TITLE}</summary>
        <p className="text-muted-foreground mt-2">
          {t("internalPromptsTechnicalKey", { key: PROMPT_OVERRIDES_KEY })}
        </p>
        {catalogOnlyGroups.length > 0 ? (
          <ul className="text-muted-foreground mt-2 list-disc pl-4">
            {catalogOnlyGroups.map((g) => (
              <li key={g.id}>{g.componentLabel} — {t("internalPromptCatalogOnly")}</li>
            ))}
          </ul>
        ) : null}
      </details>
    </div>
  );
}
