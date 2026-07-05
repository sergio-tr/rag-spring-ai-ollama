"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

type AssistantInstructionsFieldsProps = Readonly<{
  mode: "user" | "project";
  globalPersonaPrompt: string;
  projectPrompt: string;
  onGlobalPersonaPromptChange: (value: string) => void;
  onProjectPromptChange: (value: string) => void;
  personaLoading?: boolean;
  projectPromptLoading?: boolean;
  onResetAnswerInstructions?: () => void;
  onResetSourceUsageInstructions?: () => void;
}>;

/** Account- and project-level instruction layers (separate from configuration system instructions). */
export function AssistantInstructionsFields({
  mode,
  globalPersonaPrompt,
  projectPrompt,
  onGlobalPersonaPromptChange,
  onProjectPromptChange,
  personaLoading = false,
  projectPromptLoading = false,
  onResetAnswerInstructions,
  onResetSourceUsageInstructions,
}: AssistantInstructionsFieldsProps) {
  const t = useTranslations("Settings");

  if (mode === "user") {
    if (personaLoading) {
      return <p className="text-muted-foreground text-sm">{t("configLoading")}</p>;
    }
    return (
      <div className="flex min-w-0 max-w-full flex-col gap-2" data-testid="assistant-answer-instructions-field">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0 flex-1 basis-full sm:basis-auto">
            <Label htmlFor="assistant-global-persona">{t("instructionsAnswerLabel")}</Label>
            <p className="text-muted-foreground break-words text-xs">{t("instructionsAnswerHint")}</p>
          </div>
          {globalPersonaPrompt.trim() && onResetAnswerInstructions ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="max-w-full shrink-0 whitespace-normal"
              data-testid="assistant-reset-answer-instructions"
              onClick={onResetAnswerInstructions}
            >
              {t("instructionsResetToDefault")}
            </Button>
          ) : null}
        </div>
        <textarea
          id="assistant-global-persona"
          data-testid="assistant-global-persona-input"
          className="border-input bg-background min-h-28 w-full rounded-md border px-3 py-2 text-sm"
          maxLength={50_000}
          value={globalPersonaPrompt}
          onChange={(e) => onGlobalPersonaPromptChange(e.target.value)}
        />
      </div>
    );
  }

  if (projectPromptLoading) {
    return <p className="text-muted-foreground text-sm">{t("configLoading")}</p>;
  }

  return (
    <div className="flex min-w-0 max-w-full flex-col gap-2" data-testid="assistant-source-usage-instructions-field">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1 basis-full sm:basis-auto">
          <Label htmlFor="assistant-project-prompt">{t("instructionsSourceUsageLabel")}</Label>
          <p className="text-muted-foreground break-words text-xs">{t("instructionsSourceUsageHint")}</p>
        </div>
        {projectPrompt.trim() && onResetSourceUsageInstructions ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="max-w-full shrink-0 whitespace-normal"
            data-testid="assistant-reset-source-usage-instructions"
            onClick={onResetSourceUsageInstructions}
          >
            {t("instructionsResetToDefault")}
          </Button>
        ) : null}
      </div>
      <textarea
        id="assistant-project-prompt"
        data-testid="assistant-project-prompt-input"
        className="border-input bg-background min-h-28 w-full rounded-md border px-3 py-2 text-sm"
        maxLength={50_000}
        value={projectPrompt}
        onChange={(e) => onProjectPromptChange(e.target.value)}
      />
    </div>
  );
}
