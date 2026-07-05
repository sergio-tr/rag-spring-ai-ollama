"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";
import {
  projectCreateWarningMessage,
  type ProjectCreatedDialogOutcome,
} from "@/features/projects/lib/project-create-feedback";
import { useProjectCreateFeedbackStore } from "@/features/projects/lib/project-create-feedback-state";

/** Shared success/warning notifier for all project creation entry points. */
export function useProjectCreateFeedbackNotifier() {
  const t = useTranslations("Projects");
  const showWarning = useProjectCreateFeedbackStore((s) => s.showWarning);

  return useCallback(
    (outcome: ProjectCreatedDialogOutcome) => {
      const warning = projectCreateWarningMessage(outcome, t);
      if (warning) {
        showWarning(warning);
      }
    },
    [showWarning, t],
  );
}
