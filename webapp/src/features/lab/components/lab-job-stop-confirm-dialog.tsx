"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { getSafeApiErrorMessage } from "@/lib/api-client";
import { useTranslations } from "next-intl";
import { useState } from "react";

export type LabJobStopConfirmDialogProps = Readonly<{
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => Promise<void>;
  /** Optional short job id fragment for the description. */
  jobIdFragment?: string | null;
}>;

/**
 * Confirms cooperative stop of a running or queued lab evaluation job.
 */
export function LabJobStopConfirmDialog({
  open,
  onOpenChange,
  onConfirm,
  jobIdFragment,
}: LabJobStopConfirmDialogProps) {
  const t = useTranslations("Lab");
  const [pending, setPending] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  function handleOpenChange(next: boolean) {
    if (pending) return;
    setErrorMsg(null);
    onOpenChange(next);
  }

  async function confirm() {
    setErrorMsg(null);
    setPending(true);
    try {
      await onConfirm();
      handleOpenChange(false);
    } catch (e) {
      setErrorMsg(getSafeApiErrorMessage(e));
    } finally {
      setPending(false);
    }
  }

  const description =
    jobIdFragment != null && jobIdFragment.trim() !== ""
      ? t("jobStopConfirmDescription", { jobId: jobIdFragment.trim() })
      : t("jobStopConfirmDescriptionGeneric");

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent data-testid="lab-job-stop-confirm-dialog">
        <DialogHeader>
          <DialogTitle>{t("jobStopConfirmTitle")}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {errorMsg ? (
          <p className="text-destructive text-sm" role="alert">
            {errorMsg}
          </p>
        ) : null}
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={pending} onClick={() => handleOpenChange(false)}>
            {t("jobStopConfirmDismiss")}
          </Button>
          <Button
            type="button"
            variant="destructive"
            data-testid="lab-job-stop-confirm-button"
            disabled={pending}
            onClick={() => void confirm()}
          >
            {pending ? t("jobCancelling") : t("jobStopEvaluation")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
