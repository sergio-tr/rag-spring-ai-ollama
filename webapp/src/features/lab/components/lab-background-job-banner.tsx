"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import {
  labSectionHref,
  pathnameMatchesLabSection,
  pickPrimaryLabBannerRecord,
  type PersistedLabJobRecord,
} from "@/features/lab/lib/lab-job-persistence";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import type { TraceStatus } from "@/features/trace/trace-types";
import { usePathname, useRouter } from "@/navigation";
import { useTranslations } from "next-intl";

function bannerSummaryMessage(
  job: PersistedLabJobRecord,
  t: ReturnType<typeof useTranslations<"Lab">>,
): string {
  if (job.staleNotFound) {
    return t("jobRecoveryStale", { jobId: job.jobId });
  }
  const isTerminal = job.lastStatus?.terminal === true;
  if (job.stoppedWatching && !isTerminal) {
    return t("jobRecoveryStoppedWaiting", { jobId: job.jobId });
  }
  if (!isTerminal) {
    return t("jobRecoveryInflight", { jobId: job.jobId });
  }
  const statusUpper = job.lastStatus?.status?.toUpperCase() ?? "";
  if (statusUpper === "SUCCEEDED") {
    return t("jobRecoveryCompleted", { jobId: job.jobId });
  }
  return t("jobRecoveryTerminalOther", {
    jobId: job.jobId,
    status: job.lastStatus?.status ?? "unknown",
  });
}

function bannerTraceStatus(job: PersistedLabJobRecord): TraceStatus {
  if (job.staleNotFound) {
    return "warning";
  }
  const isTerminal = job.lastStatus?.terminal === true;
  const statusUpper = job.lastStatus?.status?.toUpperCase() ?? "";
  if (isTerminal && statusUpper === "FAILED") {
    return "error";
  }
  if (isTerminal) {
    return "success";
  }
  if (job.stoppedWatching) {
    return "warning";
  }
  return "in_progress";
}

/**
 * Session banner for in-flight Lab evaluations: stale server state, reconnecting stream, recovery, and summaries.
 */
export function LabBackgroundJobBanner() {
  const t = useTranslations("Lab");
  const pathname = usePathname();
  const router = useRouter();
  const records = useLabJobSessionStore((s) => s.records);
  const requestResume = useLabJobSessionStore((s) => s.requestResumeLabJob);
  const clearRecord = useLabJobSessionStore((s) => s.clearLabJobRecord);
  const dismissTerminal = useLabJobSessionStore((s) => s.dismissTerminalLabJob);

  const picked = pickPrimaryLabBannerRecord(records);
  if (!picked) {
    return null;
  }

  const job = picked;
  const onSection = pathnameMatchesLabSection(pathname, job.sectionKey);
  const targetHref = labSectionHref(job.sectionKey);
  const isTerminal = job.lastStatus?.terminal === true;
  const summaryMessage = bannerSummaryMessage(job, t);
  const traceStatus = bannerTraceStatus(job);

  function continueJob() {
    requestResume(job.sectionKey, job.jobId);
    router.push(targetHref);
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2"
      data-testid="lab-job-session-banner"
    >
      <InlineHelpStatus status={traceStatus} label={summaryMessage} className="max-w-[min(100%,42rem)] flex-1" />
      <div className="flex flex-wrap gap-2">
        {job.staleNotFound ? (
          <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => clearRecord(job.jobId)}>
            {t("jobRecoveryClearStale")}
          </Button>
        ) : (
          <>
            <Button type="button" variant="secondary" size="sm" className="shrink-0" onClick={continueJob}>
              {onSection ? t("jobRecoveryResumeHere") : t("jobRecoveryOpenSection")}
            </Button>
            {isTerminal ? (
              <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => dismissTerminal(job.jobId)}>
                {t("jobRecoveryDismiss")}
              </Button>
            ) : null}
            {isTerminal ? null : (
              <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => clearRecord(job.jobId)}>
                {t("jobRecoveryForget")}
              </Button>
            )}
          </>
        )}
      </div>
    </div>
  );
}
