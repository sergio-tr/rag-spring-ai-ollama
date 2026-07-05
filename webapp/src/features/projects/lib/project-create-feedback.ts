import type { CreateProjectOutcome } from "@/features/projects/lib/project-create-reconciliation";

export type ProjectCreatedDialogOutcome = CreateProjectOutcome;

type TranslateProjects = (key: string) => string;

/** Map a successful create outcome to an optional non-fatal warning message. */
export function projectCreateWarningMessage(
  outcome: ProjectCreatedDialogOutcome,
  t: TranslateProjects,
): string | null {
  if (outcome.activateFailed) {
    return t("createActivateWarning");
  }
  if (outcome.refreshFailed) {
    return t("createRefreshWarning");
  }
  if (outcome.responseIncomplete || outcome.reconciledFromList) {
    return t("createReconciledWarning");
  }
  return null;
}
