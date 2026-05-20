"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { useRouter } from "@/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";

function sectionHref(benchmarkKind: string | null | undefined): string {
  const k = (benchmarkKind ?? "").toUpperCase();
  if (k.includes("LLM")) return "/lab/evaluation/llm";
  if (k.includes("EMBEDDING")) return "/lab/evaluation/embedding";
  if (k.includes("RAG")) return "/lab/evaluation/rag";
  if (k.includes("CLASSIFIER")) return "/lab/classifier";
  return "/lab";
}

export function LabActiveJobsBanner() {
  const t = useTranslations("Lab");
  const router = useRouter();
  const { data, isLoading, refetch } = useActiveLabJobs();
  const [cancelMsg, setCancelMsg] = useState<string | null>(null);

  const job = data?.[0];
  if (!isLoading && (!data || data.length === 0 || !job)) {
    return null;
  }

  async function cancelJob() {
    if (!job?.jobId) return;
    setCancelMsg(null);
    try {
      await apiFetch<void>(apiProductPath(`/lab/jobs/${job.jobId}/cancel`), { method: "POST" });
      setCancelMsg(t("jobCancelRequested"));
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setCancelMsg(t("jobCancelTooLate"));
      } else {
        setCancelMsg(e instanceof Error ? e.message : String(e));
      }
    }
    await refetch();
  }

  const label = job
    ? t("activeJobBanner", {
        jobId: job.jobId,
        benchmarkKind: job.benchmarkKind ?? "unknown",
        status: job.status,
      })
    : t("activeJobBannerLoading");

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2" data-testid="lab-active-job-banner">
      <InlineHelpStatus status="in_progress" label={label} className="max-w-[min(100%,42rem)] flex-1" />
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" size="sm" className="shrink-0" onClick={() => router.push(sectionHref(job?.benchmarkKind))}>
          {t("activeJobBannerViewProgress")}
        </Button>
        {job?.cancellable ? (
          <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => void cancelJob()}>
            {t("activeJobBannerCancel")}
          </Button>
        ) : null}
      </div>
      {cancelMsg ? (
        <p className="text-muted-foreground text-xs w-full" role="status">
          {cancelMsg}
        </p>
      ) : null}
    </div>
  );
}

