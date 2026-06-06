"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import { LabJobStopConfirmDialog } from "@/features/lab/components/lab-job-stop-confirm-dialog";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { useRouter } from "@/navigation";
import type { ActiveLabJobDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { formatBenchmarkKindLabel } from "@/lib/product-copy";

function sectionHref(benchmarkKind: string | null | undefined): string {
  const k = (benchmarkKind ?? "").toUpperCase();
  if (k.includes("LLM")) return "/lab/evaluation/llm";
  if (k.includes("EMBEDDING")) return "/lab/evaluation/embedding";
  if (k.includes("RAG")) return "/lab/evaluation/rag";
  if (k.includes("CLASSIFIER")) return "/lab/classifier";
  return "/lab";
}

function ActiveJobRow(props: Readonly<{
  job: ActiveLabJobDto;
  onCancelDone: () => void;
}>) {
  const { job, onCancelDone } = props;
  const t = useTranslations("Lab");
  const router = useRouter();
  const [cancelMsg, setCancelMsg] = useState<string | null>(null);
  const [cancelConfirmOpen, setCancelConfirmOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  async function cancelJob() {
    if (!job.jobId) return;
    setCancelMsg(null);
    setCancelling(true);
    try {
      await apiFetch<void>(apiProductPath(`/lab/jobs/${job.jobId}/cancel`), { method: "POST" });
      setCancelMsg(t("jobCancelRequested"));
      onCancelDone();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setCancelMsg(t("jobCancelTooLate"));
      } else {
        setCancelMsg(e instanceof Error ? e.message : String(e));
      }
      throw e;
    } finally {
      setCancelling(false);
    }
  }

  const label = t("activeJobBanner", {
    benchmarkLabel: formatBenchmarkKindLabel(job.benchmarkKind, t),
    status: job.status,
  });

  return (
    <div className="flex flex-wrap items-center gap-2" data-testid={`lab-active-job-row-${job.jobId}`}>
      <InlineHelpStatus status="in_progress" label={label} className="max-w-[min(100%,42rem)] flex-1" />
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          className="shrink-0"
          onClick={() => router.push(sectionHref(job.benchmarkKind))}
        >
          {t("activeJobBannerViewProgress")}
        </Button>
        {job.cancellable ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="shrink-0"
            disabled={cancelling}
            onClick={() => setCancelConfirmOpen(true)}
          >
            {cancelling ? t("jobCancelling") : t("jobStopEvaluation")}
          </Button>
        ) : null}
      </div>
      {cancelMsg ? (
        <span className="text-muted-foreground w-full text-xs" role="status">
          {cancelMsg}
        </span>
      ) : null}
      <LabJobStopConfirmDialog
        open={cancelConfirmOpen}
        onOpenChange={setCancelConfirmOpen}
        jobIdFragment={job.jobId?.slice(0, 8) ?? null}
        onConfirm={cancelJob}
      />
    </div>
  );
}

export function LabActiveJobsBanner() {
  const t = useTranslations("Lab");
  const { data, isLoading, refetch } = useActiveLabJobs();

  if (isLoading) {
    return (
      <div className="rounded-md border border-border bg-muted/30 px-3 py-2" data-testid="lab-active-job-banner">
        <InlineHelpStatus status="in_progress" label={t("activeJobBannerLoading")} />
      </div>
    );
  }

  const jobs = Array.isArray(data) ? data : [];
  if (jobs.length === 0) {
    return null;
  }

  return (
    <div
      className="space-y-2 rounded-md border border-border bg-muted/30 px-3 py-2"
      data-testid="lab-active-job-banner"
    >
      {jobs.length > 1 ? (
        <p className="font-medium text-sm" data-testid="lab-active-jobs-multiple-title">
          {t("labRecoveryMultipleTitle")}
        </p>
      ) : null}
      {jobs.map((job) => (
        <ActiveJobRow key={job.jobId} job={job} onCancelDone={() => void refetch()} />
      ))}
    </div>
  );
}
