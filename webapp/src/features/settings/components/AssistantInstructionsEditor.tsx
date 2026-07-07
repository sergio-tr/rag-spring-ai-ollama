"use client";

import { useTranslations } from "next-intl";
import type { UseFormReturn } from "react-hook-form";
import { Button } from "@/components/ui/button";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
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

  function resetSystemInstructions() {
    form.setValue("llmSystemPrompt", undefined, { shouldDirty: true, shouldValidate: true });
  }

  function resetAnswerInstructions() {
    onGlobalPersonaPromptChange("");
  }

  function resetSourceUsageInstructions() {
    onProjectPromptChange("");
  }

  const hasSystemField = instructionFields.some((f) => f.key === "llmSystemPrompt");

  return (
    <div className="flex min-w-0 max-w-full flex-col gap-4" data-testid="assistant-instructions-editor">
      <p className="text-muted-foreground break-words text-xs">{t("instructionsEditorScopeNote")}</p>

      {hasSystemField ? (
        <div className="flex min-w-0 max-w-full flex-col gap-2" data-testid="assistant-system-instructions-field">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0 flex-1 basis-full sm:basis-auto">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {t("instructionsSystemLabel")}
              </h4>
              <p className="text-muted-foreground mt-1 break-words text-xs">{t("instructionsSystemHint")}</p>
            </div>
            {systemInstructions.trim() ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="max-w-full shrink-0 whitespace-normal"
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

    </div>
  );
}
