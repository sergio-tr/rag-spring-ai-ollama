"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { UseFormReturn } from "react-hook-form";
import { Button } from "@/components/ui/button";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { buildAssistantInstructionsPreview } from "@/features/settings/lib/assistant-instructions-preview";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { AssistantInstructionsFields } from "@/features/settings/components/AssistantInstructionsFields";

type AssistantInstructionsEditorProps = Readonly<{
  mode: "user" | "project";
  form: UseFormReturn<ConfigFormValues>;
  instructionFields: ConfigSchemaField[];
  fieldLabel: (fieldKey: string) => string;
  globalPersonaPrompt: string;
  projectPrompt: string;
  onGlobalPersonaPromptChange: (value: string) => void;
  onProjectPromptChange: (value: string) => void;
  personaLoading?: boolean;
  projectPromptLoading?: boolean;
}>;

export function AssistantInstructionsEditor({
  mode,
  form,
  instructionFields,
  fieldLabel,
  globalPersonaPrompt,
  projectPrompt,
  onGlobalPersonaPromptChange,
  onProjectPromptChange,
  personaLoading = false,
  projectPromptLoading = false,
}: AssistantInstructionsEditorProps) {
  const t = useTranslations("Settings");

  const systemInstructions =
    typeof form.watch("llmSystemPrompt") === "string" ? String(form.watch("llmSystemPrompt")) : "";

  const previewLayers = useMemo(
    () =>
      buildAssistantInstructionsPreview({
        mode,
        systemInstructions,
        answerInstructions: globalPersonaPrompt,
        sourceUsageInstructions: projectPrompt,
      }),
    [mode, systemInstructions, globalPersonaPrompt, projectPrompt],
  );

  function resetSystemInstructions() {
    form.setValue("llmSystemPrompt", undefined, { shouldDirty: true, shouldValidate: true });
  }

  function resetAnswerInstructions() {
    onGlobalPersonaPromptChange("");
  }

  function resetSourceUsageInstructions() {
    onProjectPromptChange("");
  }

  function statusLabel(status: (typeof previewLayers)[number]["status"]): string {
    if (status === "set") return t("instructionsPreviewLayerSet");
    if (status === "not_applicable") return t("instructionsPreviewLayerNotApplicable");
    return t("instructionsPreviewLayerDefault");
  }

  const hasSystemField = instructionFields.some((f) => f.key === "llmSystemPrompt");

  return (
    <div className="flex flex-col gap-4" data-testid="assistant-instructions-editor">
      <p className="text-muted-foreground text-xs">{t("instructionsEditorScopeNote")}</p>

      {hasSystemField ? (
        <div className="flex flex-col gap-2" data-testid="assistant-system-instructions-field">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {t("instructionsSystemLabel")}
              </h4>
              <p className="text-muted-foreground mt-1 text-xs">{t("instructionsSystemHint")}</p>
            </div>
            {systemInstructions.trim() ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="assistant-reset-system-instructions"
                onClick={resetSystemInstructions}
              >
                {t("instructionsResetToDefault")}
              </Button>
            ) : null}
          </div>
          <ConfigSchemaFieldRows
            fields={instructionFields.filter((f) => f.key === "llmSystemPrompt")}
            form={form}
            labelFor={() => ""}
            inputIdPrefix="cfg"
          />
        </div>
      ) : null}

      <AssistantInstructionsFields
        mode={mode}
        globalPersonaPrompt={globalPersonaPrompt}
        projectPrompt={projectPrompt}
        onGlobalPersonaPromptChange={onGlobalPersonaPromptChange}
        onProjectPromptChange={onProjectPromptChange}
        personaLoading={personaLoading}
        projectPromptLoading={projectPromptLoading}
        onResetAnswerInstructions={mode === "user" ? resetAnswerInstructions : undefined}
        onResetSourceUsageInstructions={mode === "project" ? resetSourceUsageInstructions : undefined}
      />

      <details
        className="rounded-md border bg-muted/20 p-3 text-sm"
        data-testid="assistant-instructions-preview"
      >
        <summary className="cursor-pointer font-medium">{t("instructionsPreviewTitle")}</summary>
        <div className="mt-3 space-y-3 text-xs">
          {previewLayers.map((layer) => (
            <div key={layer.id} className="rounded-md border bg-background/60 px-3 py-2" data-testid={`assistant-preview-layer-${layer.id}`}>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <span className="font-medium">{t(layer.labelKey as never)}</span>
                <span className="text-muted-foreground">{statusLabel(layer.status)}</span>
              </div>
              {layer.preview ? (
                <p className="text-muted-foreground mt-2 whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
                  {layer.preview}
                </p>
              ) : layer.id === "grounding" ? (
                <p className="text-muted-foreground mt-2">{t("instructionsGroundingPreviewNote")}</p>
              ) : layer.id === "abstention" ? (
                <p className="text-muted-foreground mt-2">{t("instructionsAbstentionPreviewNote")}</p>
              ) : null}
            </div>
          ))}
        </div>
      </details>

      <p className="text-muted-foreground text-[11px] leading-relaxed">{t("instructionsOutOfScopeNote")}</p>
    </div>
  );
}
