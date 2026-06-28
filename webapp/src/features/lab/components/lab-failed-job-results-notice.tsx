"use client";

import { Badge } from "@/components/ui/badge";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import type { AsyncTaskStatusDto } from "@/types/api";
import { useTranslations } from "next-intl";

export type LabFailedJobResultsNoticeProps = Readonly<{
  evaluationRunId: string;
  taskStatus: AsyncTaskStatusDto | null;
}>;

export function LabFailedJobResultsNotice({ evaluationRunId, taskStatus }: LabFailedJobResultsNoticeProps) {
  const t = useTranslations("Lab");
  const runId = evaluationRunId.trim();
  const errorMessage = taskStatus?.errorMessage?.trim()
    ? mapUserFacingErrorMessage(taskStatus.errorMessage, t, t("jobUiFailed"))
    : null;

  return (
    <div
      className="space-y-2 rounded-md border border-destructive/40 bg-destructive/5 p-4"
      data-testid="lab-failed-job-results-notice"
      role="alert"
    >
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="destructive" data-testid="lab-failed-job-status-badge">
          {t("failedJobResultsTitle")}
        </Badge>
      </div>
      <p className="text-muted-foreground text-xs">{t("failedJobResultsUnavailable")}</p>
      <p className="font-mono text-xs" data-testid="lab-failed-job-run-id">
        {t("failedJobResultsRunId", { runId: runId.slice(0, 8) })}
      </p>
      {errorMessage ? (
        <p className="text-destructive text-xs" data-testid="lab-failed-job-error-message">
          {errorMessage}
        </p>
      ) : null}
    </div>
  );
}
