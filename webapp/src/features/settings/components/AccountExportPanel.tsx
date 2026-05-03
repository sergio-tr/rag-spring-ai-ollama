"use client";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { pickExportArtifactId } from "@/features/settings/lib/account-export-artifact";
import {
  createAccountExportTraceDedupe,
  emitAccountExportTraceForTick,
  traceAccountExportFailed,
  traceAccountExportQueued,
  traceAccountExportResumedWatching,
  traceAccountExportStoppedWaiting,
} from "@/features/settings/lib/account-export-trace";
import { useAccountExportSessionStore } from "@/features/settings/store/account-export-session.store";
import {
  ApiError,
  apiDownloadBlob,
  apiFetch,
  apiProductPath,
  getSafeApiErrorMessage,
  sanitizePlainErrorTextForUi,
} from "@/lib/api-client";
import { LabJobPollTimeoutError, pollAccountJob } from "@/lib/async-task";
import type { AccountJobAcceptedDto, AsyncTaskStatusDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

const ACCOUNT_EXPORT_POLL_MAX_MS = 180_000;

export function AccountExportPanel() {
  const t = useTranslations("Settings");
  const jobId = useAccountExportSessionStore((s) => s.jobId);
  const lastStatus = useAccountExportSessionStore((s) => s.lastStatus);
  const stoppedWatching = useAccountExportSessionStore((s) => s.stoppedWatching);
  const pollTimedOut = useAccountExportSessionStore((s) => s.pollTimedOut);

  const [followingBusy, setFollowingBusy] = useState(false);
  const [downloadBusy, setDownloadBusy] = useState(false);
  const [inlineError, setInlineError] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const mountedRef = useRef(true);
  const hydrateRanRef = useRef(false);
  const traceDedupeRef = useRef(createAccountExportTraceDedupe());

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      abortRef.current?.abort();
    };
  }, []);

  const traceMessages = {
    queued: t("accountExportTraceQueued"),
    running: t("accountExportTraceRunning"),
    completed: t("accountExportTraceCompleted"),
    failed: t("accountExportTraceFailed"),
    cancelled: t("accountExportTraceCancelled"),
  };

  async function runFollowLoop(activeJobId: string, signal: AbortSignal): Promise<AsyncTaskStatusDto | null> {
    traceDedupeRef.current = createAccountExportTraceDedupe();
    try {
      return await pollAccountJob(
        activeJobId,
        (s) => {
          useAccountExportSessionStore.getState().patchFromTick(s);
          emitAccountExportTraceForTick(traceDedupeRef.current, s, activeJobId, traceMessages);
        },
        {
          signal,
          maxWaitMs: ACCOUNT_EXPORT_POLL_MAX_MS,
        },
      );
    } catch (e) {
      const isPollTimeout =
        e instanceof LabJobPollTimeoutError ||
        (e instanceof Error &&
          typeof e.message === "string" &&
          e.message.includes("job may still be running"));
      if (isPollTimeout) {
        useAccountExportSessionStore.getState().markStoppedWatching(true);
        traceAccountExportStoppedWaiting(activeJobId, t("accountExportTraceStoppedWaiting"));
        return null;
      }
      if (e instanceof DOMException && e.name === "AbortError") {
        useAccountExportSessionStore.getState().markStoppedWatching(false);
        traceAccountExportStoppedWaiting(activeJobId, t("accountExportTraceNavigatedAway"));
        return null;
      }
      if (e instanceof ApiError && e.status === 404) {
        useAccountExportSessionStore.getState().clearSession();
        if (mountedRef.current) {
          setInlineError(t("accountExportJobNotFound"));
        }
        return null;
      }
      throw e;
    }
  }

  async function startFollow(activeJobId: string, signal: AbortSignal) {
    if (!mountedRef.current) return;
    setFollowingBusy(true);
    setInlineError(null);
    try {
      await runFollowLoop(activeJobId, signal);
    } catch (e) {
      const raw = e instanceof Error ? e.message : "";
      const safe =
        sanitizePlainErrorTextForUi(raw) ||
        (e instanceof ApiError ? e.message : "") ||
        t("accountExportError");
      if (mountedRef.current) {
        setInlineError(safe);
      }
    } finally {
      if (mountedRef.current) {
        setFollowingBusy(false);
      }
    }
  }

  useEffect(() => {
    if (hydrateRanRef.current) return;
    hydrateRanRef.current = true;
    const st = useAccountExportSessionStore.getState();
    if (!st.jobId || !st.lastStatus || st.lastStatus.terminal || st.stoppedWatching) {
      return;
    }
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    void startFollow(st.jobId, abortRef.current.signal);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- one-shot hydrate from persisted session
  }, []);

  async function requestExport() {
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;

    setInlineError(null);
    setDownloadError(null);
    setFollowingBusy(true);

    try {
      const accepted = await apiFetch<AccountJobAcceptedDto>(apiProductPath("/me/account/export"), {
        method: "POST",
        signal,
      });
      useAccountExportSessionStore.getState().resetForNewExport(accepted);
      traceAccountExportQueued(accepted.jobId, t("accountExportTraceQueued"));
      await runFollowLoop(accepted.jobId, signal);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        return;
      }
      let msg: string;
      if (e instanceof ApiError) {
        msg = e.message;
      } else if (e instanceof Error) {
        msg = sanitizePlainErrorTextForUi(e.message) || e.message;
      } else {
        msg = t("accountExportError");
      }
      if (mountedRef.current) {
        setInlineError(msg || t("accountExportError"));
      }
    } finally {
      if (mountedRef.current) {
        setFollowingBusy(false);
      }
    }
  }

  async function resumeWatchingExport() {
    const activeJobId = useAccountExportSessionStore.getState().jobId;
    if (!activeJobId) return;
    traceAccountExportResumedWatching(activeJobId, t("accountExportTraceResumed"));
    useAccountExportSessionStore.getState().resumeWatching();
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    await startFollow(activeJobId, abortRef.current.signal);
  }

  async function downloadExport() {
    const artifactId = pickExportArtifactId(lastStatus?.result ?? null);
    const activeJobId = jobId ?? "";
    if (!artifactId) {
      setDownloadError(t("accountExportNoArtifact"));
      return;
    }
    setDownloadBusy(true);
    setDownloadError(null);
    try {
      const blob = await apiDownloadBlob(apiProductPath(`/me/account/export/${artifactId}/download`));
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "account-export.zip";
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : getSafeApiErrorMessage(e);
      setDownloadError(msg);
      if (activeJobId) {
        traceAccountExportFailed(activeJobId, t("accountExportTraceDownloadFailed"));
      }
    } finally {
      setDownloadBusy(false);
    }
  }

  const succeeded =
    lastStatus?.terminal === true && lastStatus.status.toUpperCase() === "SUCCEEDED";
  const failed = lastStatus?.terminal === true && lastStatus.status.toUpperCase() === "FAILED";
  const artifactId = pickExportArtifactId(lastStatus?.result ?? null);

  const phaseMessage = (() => {
    if (!lastStatus || !jobId) return null;
    if (failed) {
      const hint =
        sanitizePlainErrorTextForUi(lastStatus.errorMessage ?? "") ||
        t("accountExportError");
      return hint;
    }
    if (succeeded) {
      return t("accountExportPhaseReady");
    }
    const st = lastStatus.status.toUpperCase();
    if (st === "ACCEPTED" || st === "QUEUED") return t("accountExportPhaseQueued");
    if (st === "RUNNING") {
      const line = lastStatus.progressText?.trim();
      return line ? `${t("accountExportPhaseRunning")} ${line}` : t("accountExportPhaseRunning");
    }
    return null;
  })();

  let stoppedMessage: string | null = null;
  if (stoppedWatching) {
    stoppedMessage = pollTimedOut
      ? t("accountExportStoppedWatchingTimeout")
      : t("accountExportStoppedWatchingNavigated");
  }

  const requestDisabled = followingBusy || (!!lastStatus && !lastStatus.terminal);

  return (
    <Card id="settings-account-export" data-testid="settings-account-export" className="scroll-mt-24">
      <CardHeader>
        <CardTitle>{t("accountExportTitle")}</CardTitle>
        <CardDescription>{t("accountExportDescription")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Button
          type="button"
          data-testid="account-export-request"
          onClick={() => void requestExport()}
          disabled={requestDisabled}
        >
          {followingBusy ? t("accountExportRunning") : t("accountExportCta")}
        </Button>

        {(phaseMessage || stoppedMessage || inlineError || downloadError) && (
          <div className="flex flex-col gap-2 text-sm" data-testid="account-export-status">
            {phaseMessage && !inlineError && (
              <output className="text-muted-foreground block">{phaseMessage}</output>
            )}
            {inlineError && (
              <p className="text-destructive" role="alert">
                {inlineError}
              </p>
            )}
            {stoppedMessage && (
              <output className="text-muted-foreground block border-l-2 border-amber-500/60 pl-2">
                {stoppedMessage}{" "}
                <span className="block text-xs">{t("accountExportPollHint")}</span>
              </output>
            )}
            {downloadError && (
              <p className="text-destructive" role="alert">
                {downloadError}
              </p>
            )}
          </div>
        )}

        {stoppedWatching && jobId && !lastStatus?.terminal && (
          <Button
            type="button"
            variant="secondary"
            data-testid="account-export-resume"
            onClick={() => void resumeWatchingExport()}
            disabled={followingBusy}
          >
            {t("accountExportResumePolling")}
          </Button>
        )}

        {succeeded && artifactId && (
          <Button
            type="button"
            variant="outline"
            data-testid="account-export-download"
            onClick={() => void downloadExport()}
            disabled={downloadBusy || followingBusy}
          >
            {downloadBusy ? t("accountExportDownloading") : t("accountExportDownloadZip")}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
