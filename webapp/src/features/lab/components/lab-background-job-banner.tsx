"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import {
  labSectionHref,
  pathnameMatchesLabSection,
  pickPrimaryLabBannerRecord,
} from "@/features/lab/lib/lab-job-persistence";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import type { TraceStatus } from "@/features/trace/trace-types";
import { usePathname, useRouter } from "@/navigation";
import { useTranslations } from "next-intl";

/**
 * Session banner for Lab async jobs: stale server state, stopped waiting, in-flight recovery, and completed/failed summaries.
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
  const st = job.lastStatus?.status?.toUpperCase() ?? "";

  const summaryMessage = job.staleNotFound
    ? t("jobRecoveryStale", { jobId: job.jobId })
    : job.stoppedWatching && !isTerminal
      ? t("jobRecoveryStoppedWaiting", { jobId: job.jobId })
      : isTerminal
        ? st === "SUCCEEDED"
          ? t("jobRecoveryCompleted", { jobId: job.jobId })
          : t("jobRecoveryTerminalOther", {
              jobId: job.jobId,
              status: job.lastStatus?.status ?? "unknown",
            })
        : t("jobRecoveryInflight", { jobId: job.jobId });

  const traceStatus: TraceStatus = job.staleNotFound
    ? "warning"
    : isTerminal && st === "FAILED"
      ? "error"
      : isTerminal
        ? "success"
        : job.stoppedWatching
          ? "warning"
          : "in_progress";

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
            <Button type="button" variant="secondary" size="sm" className="shrink-0" onClick={() => void continueJob()}>
              {onSection ? t("jobRecoveryResumeHere") : t("jobRecoveryOpenSection")}
            </Button>
            {isTerminal ? (
              <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => dismissTerminal(job.jobId)}>
                {t("jobRecoveryDismiss")}
              </Button>
            ) : null}
            {job.stoppedWatching ? (
              <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => clearRecord(job.jobId)}>
                {t("jobRecoveryDismiss")}
              </Button>
            ) : null}
            {!isTerminal && !job.stoppedWatching ? (
              <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => clearRecord(job.jobId)}>
                {t("jobRecoveryForget")}
              </Button>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}
