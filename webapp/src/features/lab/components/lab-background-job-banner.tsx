"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { useTranslations } from "next-intl";

/**
 * Session-level hint when the user stopped waiting for a Lab job; server work may continue.
 */
export function LabBackgroundJobBanner() {
  const t = useTranslations("Lab");
  const hint = useLabJobSessionStore((s) => s.backgroundHint);
  const clear = useLabJobSessionStore((s) => s.clearBackgroundHint);

  if (!hint?.stoppedWaiting) {
    return null;
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2"
      data-testid="lab-background-job-banner"
    >
      <InlineHelpStatus
        status="warning"
        label={t("backgroundJobBannerLabel", { jobId: hint.jobId })}
        className="max-w-[min(100%,42rem)] flex-1 border-amber-500/40"
      />
      <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={() => clear()}>
        {t("backgroundJobBannerDismiss")}
      </Button>
    </div>
  );
}
