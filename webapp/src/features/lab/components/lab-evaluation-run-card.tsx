"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import {
  createLabJobTraceDedupe,
  emitLabJobTraceForTick,
  traceLabJobQueued,
  traceLabJobStoppedWaiting,
} from "@/features/lab/lib/lab-job-trace";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { followLabJob } from "@/lib/lab-job-follow";
import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import { useAppStore } from "@/store/app.store";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { useRef, useState } from "react";

export type LabEvaluationRunCardProps = {
  /** POST target under the product API, e.g. `/lab/evaluations/llm`. */
  evalBasePath: "/lab/evaluations/llm" | "/lab/evaluations/rag";
  cardTitle: string;
  cardDescription: string;
  runButtonTestId: string;
  radioGroupName: string;
  /** Optional copy above the card (e.g. RAG-specific help). */
  introBeforeCard?: ReactNode;
};

/**
 * Shared LLM vs RAG lab evaluation runner (sync POST or async job + poll/SSE).
 * Keeps a single implementation so Sonar CPD and maintenance stay reasonable.
 */
export function LabEvaluationRunCard({
  evalBasePath,
  cardTitle,
  cardDescription,
  runButtonTestId,
  radioGroupName,
  introBeforeCard,
}: LabEvaluationRunCardProps) {
  const t = useTranslations("Lab");
  const { data: labStatus } = useLabStatus();
  const activeProject = useAppStore((s) => s.activeProject);

  const [syncMode, setSyncMode] = useState(false);
  const [followMode, setFollowMode] = useState<LabJobFollowMode>("poll");
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<unknown>(null);
  const [accepted, setAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [taskStatus, setTaskStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [stoppedWaiting, setStoppedWaiting] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const traceDedupeRef = useRef(createLabJobTraceDedupe());

  const datasetsReady = labStatus?.datasets.enabled ?? false;

  async function run() {
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;
    setRunning(true);
    setErr(null);
    setResult(null);
    setAccepted(null);
    setTaskStatus(null);
    setStoppedWaiting(false);
    traceDedupeRef.current = createLabJobTraceDedupe();
    useLabJobSessionStore.getState().clearBackgroundHint();
    let asyncAccepted: LabJobAcceptedDto | null = null;
    try {
      const params = new URLSearchParams();
      if (syncMode) {
        params.set("sync", "true");
      }
      if (activeProject?.id) {
        params.set("projectId", activeProject.id);
      }
      const qs = params.toString();
      const path = qs ? `${evalBasePath}?${qs}` : evalBasePath;
      const url = apiProductPath(path);

      if (syncMode) {
        const data = await apiFetch<unknown>(url, { method: "POST", signal });
        setResult(data);
        return;
      }

      const acc = await apiFetch<LabJobAcceptedDto>(url, { method: "POST", signal });
      asyncAccepted = acc;
      setAccepted(acc);
      if (!traceDedupeRef.current.acceptedEmitted) {
        traceDedupeRef.current.acceptedEmitted = true;
        traceLabJobQueued(acc.jobId, t("traceJobQueued"));
      }
      const traceMessages = {
        queued: t("traceJobQueued"),
        running: t("traceJobRunning"),
        completed: t("traceJobCompleted"),
        failed: t("traceJobFailed"),
        cancelled: t("traceJobCancelled"),
      };
      const done = await followLabJob(
        acc,
        (s) => {
          setTaskStatus(s);
          emitLabJobTraceForTick(traceDedupeRef.current, s, acc.jobId, traceMessages);
        },
        {
          mode: followMode,
          signal,
        },
      );
      setResult(done.result);
      const hint = useLabJobSessionStore.getState().backgroundHint;
      if (hint?.jobId === acc.jobId) {
        useLabJobSessionStore.getState().clearBackgroundHint();
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        setErr(t("jobCancelled"));
        setStoppedWaiting(true);
        if (asyncAccepted?.jobId) {
          traceLabJobStoppedWaiting(asyncAccepted.jobId, t("traceStoppedWaiting"));
          useLabJobSessionStore.getState().setBackgroundHint({
            jobId: asyncAccepted.jobId,
            stoppedWaiting: true,
          });
        }
      } else {
        setErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-muted-foreground border-l-4 border-primary/40 pl-3 text-sm">{t("adrDisclaimer")}</p>
      {introBeforeCard ?? null}

      <Card>
        <CardHeader>
          <CardTitle>{cardTitle}</CardTitle>
          <CardDescription>{cardDescription}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="size-4 rounded border"
                checked={syncMode}
                onChange={(e) => setSyncMode(e.target.checked)}
                disabled={running}
              />
              {t("syncModeLabel")}
            </label>
            {syncMode ? null : (
              <div className="flex flex-col gap-1">
                <span className="text-muted-foreground text-xs">{t("followModeLabel")}</span>
                <div className="flex gap-3 text-sm">
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      name={radioGroupName}
                      checked={followMode === "poll"}
                      onChange={() => setFollowMode("poll")}
                      disabled={running}
                    />
                    {t("followModePoll")}
                  </label>
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      name={radioGroupName}
                      checked={followMode === "sse"}
                      onChange={() => setFollowMode("sse")}
                      disabled={running}
                    />
                    {t("followModeSse")}
                  </label>
                </div>
              </div>
            )}
          </div>

          {activeProject ? (
            <p className="text-muted-foreground text-xs">
              {t("projectScopeActive", { name: activeProject.name })}
            </p>
          ) : (
            <p className="text-muted-foreground text-xs">{t("projectScopeNone")}</p>
          )}

          {datasetsReady ? null : (
            <output className="block text-amber-600 text-sm dark:text-amber-500">
              {t("datasetsDisabledWarn")}
            </output>
          )}

          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              data-testid={runButtonTestId}
              disabled={running || !datasetsReady}
              onClick={() => void run()}
            >
              {running ? t("evalRunning") : t("runEval")}
            </Button>
            {running ? (
              <Button type="button" variant="outline" onClick={() => abortRef.current?.abort()}>
                {t("jobCancel")}
              </Button>
            ) : null}
          </div>

          {err ? (
            <p className="text-destructive text-sm" role="alert">
              {err}
            </p>
          ) : null}

          {syncMode || (!accepted && !taskStatus) ? null : (
            <LabJobPanel
              accepted={accepted}
              taskStatus={taskStatus}
              queuedHint={!!accepted && !taskStatus}
              stoppedWaiting={stoppedWaiting}
            />
          )}

          {result != null ? (
            <>
              <Label className="text-muted-foreground">{t("evalResultTitle")}</Label>
              <pre className="bg-muted/40 max-h-[480px] overflow-auto rounded-md border border-border p-3 text-xs">
                {JSON.stringify(result, null, 2)}
              </pre>
            </>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
