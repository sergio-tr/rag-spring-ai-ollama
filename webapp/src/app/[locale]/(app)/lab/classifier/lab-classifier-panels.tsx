"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { classifierModelsQueryKey } from "@/features/lab/hooks/use-classifier-registry";
import { asyncTaskDtoFromSnapshot, type PersistedLabJobRecord } from "@/features/lab/lib/lab-job-persistence";
import {
  createLabJobTraceDedupe,
  emitLabJobTraceForTick,
  traceLabJobQueued,
  traceLabJobResumedWatching,
  traceLabJobStoppedWaiting,
} from "@/features/lab/lib/lab-job-trace";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { useQueryClient } from "@tanstack/react-query";
import { LabJobPollTimeoutError } from "@/lib/async-task";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { followLabJob } from "@/lib/lab-job-follow";
import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";

/** Local watchdog for classifier-eval polling — server-side runs may continue beyond this window. */
const CLASSIFIER_EVAL_POLL_MAX_MS = 15 * 60 * 1000;

export function LabClassifierTrainPanel(
  props: Readonly<{ classifierOk: boolean; projectId?: string }>,
) {
  const { classifierOk, projectId } = props;
  const t = useTranslations("Lab");
  const qc = useQueryClient();

  const [modelName, setModelName] = useState("lab-model");
  const [trainFile, setTrainFile] = useState<File | null>(null);
  const [trainSync, setTrainSync] = useState(false);
  const [trainFollowMode, setTrainFollowMode] = useState<LabJobFollowMode>("poll");
  const [trainOut, setTrainOut] = useState<unknown>(null);
  const [trainErr, setTrainErr] = useState<string | null>(null);
  const [trainRunning, setTrainRunning] = useState(false);
  const [trainAccepted, setTrainAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [trainStatus, setTrainStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [trainStoppedWaiting, setTrainStoppedWaiting] = useState(false);
  const trainAbortRef = useRef<AbortController | null>(null);
  const trainTraceDedupeRef = useRef(createLabJobTraceDedupe());
  const mountedTrainRef = useRef(true);

  useEffect(() => {
    mountedTrainRef.current = true;
    return () => {
      mountedTrainRef.current = false;
      trainAbortRef.current?.abort();
    };
  }, []);

  const hydratedTrainRef = useRef(false);
  useEffect(() => {
    if (hydratedTrainRef.current) return;
    hydratedTrainRef.current = true;
    const rec = useLabJobSessionStore.getState().pickLatestForSection("classifier-train");
    if (!rec || rec.staleNotFound) return;
    queueMicrotask(() => {
      setTrainAccepted(rec.accepted);
      setTrainFollowMode(rec.followMode);
      if (rec.lastStatus) {
        setTrainStatus(asyncTaskDtoFromSnapshot(rec.jobId, rec.lastStatus));
      }
      setTrainStoppedWaiting(rec.stoppedWatching);
    });
  }, []);

  const resumeNonceTrain = useLabJobSessionStore((s) => s.resumeNonce);

  const resumeTrainFromPersisted = useCallback(
    async (rec: PersistedLabJobRecord) => {
      trainAbortRef.current?.abort();
      trainAbortRef.current = new AbortController();
      const signal = trainAbortRef.current.signal;
      setTrainAccepted(rec.accepted);
      setTrainFollowMode(rec.followMode);
      trainTraceDedupeRef.current = createLabJobTraceDedupe();
      traceLabJobResumedWatching(rec.jobId, t("traceJobResumedWatching"));
      setTrainRunning(true);
      setTrainErr(null);
      setTrainStoppedWaiting(false);
      try {
        const traceMessages = {
          queued: t("traceJobQueued"),
          running: t("traceJobRunning"),
          completed: t("traceJobCompleted"),
          failed: t("traceJobFailed"),
          cancelled: t("traceJobCancelled"),
        };
        const done = await followLabJob(
          rec.accepted,
          (s) => {
            setTrainStatus(s);
            useLabJobSessionStore.getState().patchLabJobFromTick(rec.jobId, s);
            emitLabJobTraceForTick(trainTraceDedupeRef.current, s, rec.jobId, traceMessages);
          },
          { mode: rec.followMode, signal },
        );
        setTrainOut(done.result);
        void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") {
          if (!mountedTrainRef.current) return;
          setTrainErr(t("jobCancelled"));
          setTrainStoppedWaiting(true);
          traceLabJobStoppedWaiting(rec.jobId, t("traceStoppedWaiting"));
          useLabJobSessionStore.getState().setLabJobStoppedWatching(rec.jobId, true);
        } else if (e instanceof ApiError && e.status === 404) {
          if (!mountedTrainRef.current) return;
          useLabJobSessionStore.getState().markLabJobStaleNotFound(rec.jobId);
          setTrainErr(t("jobRecoveryStaleShort"));
        } else {
          if (!mountedTrainRef.current) return;
          setTrainErr(e instanceof Error ? e.message : t("evalError"));
        }
      } finally {
        if (mountedTrainRef.current) {
          setTrainRunning(false);
        }
      }
    },
    [qc, t],
  );

  useEffect(() => {
    const rec = useLabJobSessionStore.getState().consumePendingResume("classifier-train");
    if (!rec) return;
    queueMicrotask(() => {
      void resumeTrainFromPersisted(rec);
    });
  }, [resumeNonceTrain, resumeTrainFromPersisted]);

  async function runTrain() {
    if (!trainFile) {
      setTrainErr(t("classifierTrainFileRequired"));
      return;
    }
    trainAbortRef.current?.abort();
    trainAbortRef.current = new AbortController();
    const signal = trainAbortRef.current.signal;
    setTrainRunning(true);
    setTrainErr(null);
    setTrainOut(null);
    setTrainAccepted(null);
    setTrainStatus(null);
    setTrainStoppedWaiting(false);
    trainTraceDedupeRef.current = createLabJobTraceDedupe();
    let trainAsyncAccepted: LabJobAcceptedDto | null = null;
    try {
      const fd = new FormData();
      fd.append("file", trainFile);
      fd.append("model_name", modelName);
      fd.append("epochs", "50");
      fd.append("batch_size", "8");

      const params = new URLSearchParams();
      if (trainSync) {
        params.set("sync", "true");
      }
      if (projectId) {
        params.set("projectId", projectId);
      }
      const q = params.toString();
      const path = `/lab/classifier/train${q ? `?${q}` : ""}`;

      if (trainSync) {
        const data = await apiFetch<unknown>(apiProductPath(path), {
          method: "POST",
          body: fd,
          signal,
        });
        setTrainOut(data);
        return;
      }

      const accepted = await apiFetch<LabJobAcceptedDto>(apiProductPath(path), {
        method: "POST",
        body: fd,
        signal,
      });
      trainAsyncAccepted = accepted;
      setTrainAccepted(accepted);
      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
        accepted,
        sectionKey: "classifier-train",
        followMode: trainFollowMode,
        taskTypeHint: "CLASSIFIER_TRAIN",
      });
      if (!trainTraceDedupeRef.current.acceptedEmitted) {
        trainTraceDedupeRef.current.acceptedEmitted = true;
        traceLabJobQueued(accepted.jobId, t("traceJobQueued"));
      }
      const traceMessages = {
        queued: t("traceJobQueued"),
        running: t("traceJobRunning"),
        completed: t("traceJobCompleted"),
        failed: t("traceJobFailed"),
        cancelled: t("traceJobCancelled"),
      };
      const done = await followLabJob(
        accepted,
        (s) => {
          setTrainStatus(s);
          useLabJobSessionStore.getState().patchLabJobFromTick(accepted.jobId, s);
          emitLabJobTraceForTick(trainTraceDedupeRef.current, s, accepted.jobId, traceMessages);
        },
        {
          mode: trainFollowMode,
          signal,
        },
      );
      setTrainOut(done.result);
      void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        if (!mountedTrainRef.current) return;
        setTrainErr(t("jobCancelled"));
        setTrainStoppedWaiting(true);
        if (trainAsyncAccepted?.jobId) {
          traceLabJobStoppedWaiting(trainAsyncAccepted.jobId, t("traceStoppedWaiting"));
          useLabJobSessionStore.getState().setLabJobStoppedWatching(trainAsyncAccepted.jobId, true);
        }
      } else if (e instanceof ApiError && e.status === 404 && trainAsyncAccepted?.jobId) {
        if (!mountedTrainRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(trainAsyncAccepted.jobId);
        setTrainErr(t("jobRecoveryStaleShort"));
      } else {
        if (!mountedTrainRef.current) return;
        setTrainErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      if (mountedTrainRef.current) {
        setTrainRunning(false);
      }
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("classifierTrainSubmit")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 rounded border"
            checked={trainSync}
            onChange={(e) => setTrainSync(e.target.checked)}
            disabled={trainRunning}
          />
          {t("syncModeLabel")}
        </label>
        <details className="text-xs">
          <summary className="cursor-pointer text-muted-foreground">{t("labAdvancedOptionsSummary")}</summary>
          <div className="mt-2 space-y-3">
            {trainSync ? null : (
              <div className="flex flex-wrap gap-3 text-sm">
                <label className="flex items-center gap-1.5">
                  <input
                    type="radio"
                    name="follow-train"
                    checked={trainFollowMode === "poll"}
                    onChange={() => setTrainFollowMode("poll")}
                    disabled={trainRunning}
                  />
                  {t("followModePoll")}
                </label>
                <label className="flex items-center gap-1.5">
                  <input
                    type="radio"
                    name="follow-train"
                    checked={trainFollowMode === "sse"}
                    onChange={() => setTrainFollowMode("sse")}
                    disabled={trainRunning}
                  />
                  {t("followModeSse")}
                </label>
              </div>
            )}
            <p className="text-muted-foreground leading-relaxed">{t("labAdvancedClassifierJobHelp")}</p>
          </div>
        </details>
        <div className="grid gap-2">
          <Label htmlFor="cmodel">{t("classifierModelName")}</Label>
          <Input id="cmodel" value={modelName} onChange={(e) => setModelName(e.target.value)} />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="cfile">{t("classifierTrainFile")}</Label>
          <Input
            id="cfile"
            type="file"
            accept=".xlsx,.xls"
            onChange={(e) => setTrainFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            data-testid="lab-classifier-train"
            disabled={trainRunning || !classifierOk}
            onClick={() => void runTrain()}
          >
            {trainRunning ? t("evalRunning") : t("classifierTrainSubmit")}
          </Button>
          {trainRunning ? (
            <Button type="button" variant="outline" onClick={() => trainAbortRef.current?.abort()}>
              {t("jobCancel")}
            </Button>
          ) : null}
        </div>
        {trainErr ? <p className="text-destructive text-sm">{trainErr}</p> : null}
        {!trainSync && (trainAccepted || trainStatus) ? (
          <LabJobPanel
            accepted={trainAccepted}
            taskStatus={trainStatus}
            queuedHint={!!trainAccepted && !trainStatus}
            stoppedWaiting={trainStoppedWaiting}
          />
        ) : null}
        {trainOut != null ? (
          <pre className="bg-muted/40 max-h-[240px] overflow-auto rounded-md border p-3 text-xs">
            {JSON.stringify(trainOut, null, 2)}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function LabClassifierEvalPanel(
  props: Readonly<{ classifierOk: boolean; projectId?: string }>,
) {
  const { classifierOk, projectId } = props;
  const t = useTranslations("Lab");
  const qc = useQueryClient();

  const [evalModelId, setEvalModelId] = useState("");
  const [evalFile, setEvalFile] = useState<File | null>(null);
  const [evalSync, setEvalSync] = useState(false);
  const [evalFollowMode, setEvalFollowMode] = useState<LabJobFollowMode>("poll");
  const [evalOut, setEvalOut] = useState<unknown>(null);
  const [evalErr, setEvalErr] = useState<string | null>(null);
  const [evalRunning, setEvalRunning] = useState(false);
  const [evalAccepted, setEvalAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [evalStatus, setEvalStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [evalStoppedWaiting, setEvalStoppedWaiting] = useState(false);
  const [evalPollTimedOut, setEvalPollTimedOut] = useState(false);
  const [watchStartedAtMs, setWatchStartedAtMs] = useState<number | null>(null);
  /** Forces re-renders once per second while watching so elapsed time stays fresh (no setState in effect init). */
  const [, setElapsedBump] = useState(0);
  const evalAbortRef = useRef<AbortController | null>(null);
  const evalTraceDedupeRef = useRef(createLabJobTraceDedupe());
  const mountedEvalRef = useRef(true);

  useEffect(() => {
    mountedEvalRef.current = true;
    return () => {
      mountedEvalRef.current = false;
      evalAbortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (watchStartedAtMs == null || evalSync) {
      return undefined;
    }
    const id = window.setInterval(() => setElapsedBump((x) => x + 1), 1000);
    return () => clearInterval(id);
  }, [watchStartedAtMs, evalSync]);

  const watchElapsedSeconds =
    watchStartedAtMs != null && !evalSync
      ? Math.max(
          0,
          // eslint-disable-next-line react-hooks/purity -- bump-driven clock display between Lab polls
          Math.floor((Date.now() - watchStartedAtMs) / 1000),
        )
      : undefined;

  const hydratedEvalRef = useRef(false);
  useEffect(() => {
    if (hydratedEvalRef.current) return;
    hydratedEvalRef.current = true;
    const rec = useLabJobSessionStore.getState().pickLatestForSection("classifier-eval");
    if (!rec || rec.staleNotFound) return;
    queueMicrotask(() => {
      setEvalAccepted(rec.accepted);
      setEvalFollowMode(rec.followMode);
      if (rec.lastStatus) {
        setEvalStatus(asyncTaskDtoFromSnapshot(rec.jobId, rec.lastStatus));
      }
      setEvalStoppedWaiting(rec.stoppedWatching);
      setEvalPollTimedOut(rec.pollTimedOut);
      if (!rec.lastStatus?.terminal && !rec.staleNotFound) {
        setWatchStartedAtMs(rec.startedAtMs);
      }
    });
  }, []);

  const resumeNonceEval = useLabJobSessionStore((s) => s.resumeNonce);

  async function followClassifierEvalAccepted(
    accepted: LabJobAcceptedDto,
    signal: AbortSignal,
    modeOverride?: LabJobFollowMode,
  ) {
    const traceMessages = {
      queued: t("traceJobQueued"),
      running: t("traceJobRunning"),
      completed: t("traceJobCompleted"),
      failed: t("traceJobFailed"),
      cancelled: t("traceJobCancelled"),
    };
    const mode = modeOverride ?? evalFollowMode;
    return followLabJob(
      accepted,
      (s) => {
        setEvalStatus(s);
        useLabJobSessionStore.getState().patchLabJobFromTick(accepted.jobId, s);
        emitLabJobTraceForTick(evalTraceDedupeRef.current, s, accepted.jobId, traceMessages);
      },
      {
        mode,
        signal,
        maxWaitMs: CLASSIFIER_EVAL_POLL_MAX_MS,
      },
    );
  }

  function finalizeSuccessfulClassifierEval(done: AsyncTaskStatusDto) {
    setEvalOut(done.result);
    void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
    setEvalPollTimedOut(false);
  }

  async function resumeClassifierEvalFromPersisted(rec: PersistedLabJobRecord) {
    if (evalSync) return;
    evalAbortRef.current?.abort();
    evalAbortRef.current = new AbortController();
    const signal = evalAbortRef.current.signal;
    setEvalAccepted(rec.accepted);
    setEvalFollowMode(rec.followMode);
    evalTraceDedupeRef.current = createLabJobTraceDedupe();
    traceLabJobResumedWatching(rec.jobId, t("traceJobResumedWatching"));
    setEvalPollTimedOut(false);
    setEvalErr(null);
    setEvalStoppedWaiting(false);
    setWatchStartedAtMs(Date.now());
    setEvalRunning(true);
    try {
      const done = await followClassifierEvalAccepted(rec.accepted, signal, rec.followMode);
      finalizeSuccessfulClassifierEval(done);
    } catch (e) {
      if (e instanceof LabJobPollTimeoutError) {
        if (!mountedEvalRef.current) return;
        useLabJobSessionStore.getState().patchLabJobPollTimedOut(rec.jobId, e.lastStatus);
        setEvalPollTimedOut(true);
        setEvalErr(t("jobPollTimeout"));
        if (e.lastStatus) {
          setEvalStatus(e.lastStatus);
        }
      } else if (e instanceof DOMException && e.name === "AbortError") {
        if (!mountedEvalRef.current) return;
        setEvalErr(t("jobCancelled"));
        setEvalStoppedWaiting(true);
        traceLabJobStoppedWaiting(rec.jobId, t("traceStoppedWaiting"));
        useLabJobSessionStore.getState().setLabJobStoppedWatching(rec.jobId, true);
      } else if (e instanceof ApiError && e.status === 404) {
        if (!mountedEvalRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(rec.jobId);
        setEvalErr(t("jobRecoveryStaleShort"));
      } else {
        if (!mountedEvalRef.current) return;
        setEvalErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      if (mountedEvalRef.current) {
        setEvalRunning(false);
      }
    }
  }

  useEffect(() => {
    const rec = useLabJobSessionStore.getState().consumePendingResume("classifier-eval");
    if (!rec) return;
    queueMicrotask(() => {
      void resumeClassifierEvalFromPersisted(rec);
    });
    // Resume is intentionally driven only by resumeNonce bumps (banner / navigation).
    // eslint-disable-next-line react-hooks/exhaustive-deps -- avoid re-running on every render
  }, [resumeNonceEval]);

  async function resumeClassifierEvalWatch() {
    if (!evalAccepted || evalSync) {
      return;
    }
    evalAbortRef.current?.abort();
    evalAbortRef.current = new AbortController();
    const signal = evalAbortRef.current.signal;
    setEvalPollTimedOut(false);
    setEvalErr(null);
    setEvalStoppedWaiting(false);
    traceLabJobResumedWatching(evalAccepted.jobId, t("traceJobResumedWatching"));
    setEvalRunning(true);
    try {
      const done = await followClassifierEvalAccepted(evalAccepted, signal);
      finalizeSuccessfulClassifierEval(done);
    } catch (e) {
      if (e instanceof LabJobPollTimeoutError) {
        if (!mountedEvalRef.current) return;
        useLabJobSessionStore.getState().patchLabJobPollTimedOut(evalAccepted.jobId, e.lastStatus);
        setEvalPollTimedOut(true);
        setEvalErr(t("jobPollTimeout"));
        if (e.lastStatus) {
          setEvalStatus(e.lastStatus);
        }
      } else if (e instanceof DOMException && e.name === "AbortError") {
        if (!mountedEvalRef.current) return;
        setEvalErr(t("jobCancelled"));
        setEvalStoppedWaiting(true);
        traceLabJobStoppedWaiting(evalAccepted.jobId, t("traceStoppedWaiting"));
        useLabJobSessionStore.getState().setLabJobStoppedWatching(evalAccepted.jobId, true);
      } else if (e instanceof ApiError && e.status === 404) {
        if (!mountedEvalRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(evalAccepted.jobId);
        setEvalErr(t("jobRecoveryStaleShort"));
      } else {
        if (!mountedEvalRef.current) return;
        setEvalErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      if (mountedEvalRef.current) {
        setEvalRunning(false);
      }
    }
  }

  async function runEval() {
    evalAbortRef.current?.abort();
    evalAbortRef.current = new AbortController();
    const signal = evalAbortRef.current.signal;
    setEvalRunning(true);
    setEvalErr(null);
    setEvalOut(null);
    setEvalAccepted(null);
    setEvalStatus(null);
    setEvalStoppedWaiting(false);
    setEvalPollTimedOut(false);
    setWatchStartedAtMs(null);
    evalTraceDedupeRef.current = createLabJobTraceDedupe();
    let evalAsyncAccepted: LabJobAcceptedDto | null = null;
    try {
      const fd = new FormData();
      if (evalFile) {
        fd.append("file", evalFile);
      }
      const params = new URLSearchParams({ includeImages: "true" });
      if (evalSync) {
        params.set("sync", "true");
      }
      if (evalModelId.trim()) {
        params.set("modelId", evalModelId.trim());
      }
      if (projectId) {
        params.set("projectId", projectId);
      }

      const path = `/lab/classifier/evaluate?${params.toString()}`;

      if (evalSync) {
        const data = await apiFetch<unknown>(apiProductPath(path), {
          method: "POST",
          body: evalFile ? fd : undefined,
          signal,
        });
        setEvalOut(data);
        void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
        return;
      }

      const accepted = await apiFetch<LabJobAcceptedDto>(apiProductPath(path), {
        method: "POST",
        body: evalFile ? fd : undefined,
        signal,
      });
      evalAsyncAccepted = accepted;
      setEvalAccepted(accepted);
      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
        accepted,
        sectionKey: "classifier-eval",
        followMode: evalFollowMode,
        taskTypeHint: "CLASSIFIER_EVAL",
      });
      setWatchStartedAtMs(Date.now());
      if (!evalTraceDedupeRef.current.acceptedEmitted) {
        evalTraceDedupeRef.current.acceptedEmitted = true;
        traceLabJobQueued(accepted.jobId, t("traceJobQueued"));
      }
      const done = await followClassifierEvalAccepted(accepted, signal);
      finalizeSuccessfulClassifierEval(done);
    } catch (e) {
      if (e instanceof LabJobPollTimeoutError) {
        if (!mountedEvalRef.current) return;
        if (evalAsyncAccepted?.jobId) {
          useLabJobSessionStore.getState().patchLabJobPollTimedOut(evalAsyncAccepted.jobId, e.lastStatus);
        }
        setEvalPollTimedOut(true);
        setEvalErr(t("jobPollTimeout"));
        if (e.lastStatus) {
          setEvalStatus(e.lastStatus);
        }
      } else if (e instanceof DOMException && e.name === "AbortError") {
        if (!mountedEvalRef.current) return;
        setEvalErr(t("jobCancelled"));
        setEvalStoppedWaiting(true);
        if (evalAsyncAccepted?.jobId) {
          traceLabJobStoppedWaiting(evalAsyncAccepted.jobId, t("traceStoppedWaiting"));
          useLabJobSessionStore.getState().setLabJobStoppedWatching(evalAsyncAccepted.jobId, true);
        }
      } else if (e instanceof ApiError && e.status === 404 && evalAsyncAccepted?.jobId) {
        if (!mountedEvalRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(evalAsyncAccepted.jobId);
        setEvalErr(t("jobRecoveryStaleShort"));
      } else {
        if (!mountedEvalRef.current) return;
        setEvalErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      if (mountedEvalRef.current) {
        setEvalRunning(false);
      }
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("classifierEvalSubmit")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 rounded border"
            checked={evalSync}
            onChange={(e) => setEvalSync(e.target.checked)}
            disabled={evalRunning}
          />
          {t("syncModeLabel")}
        </label>
        <details className="text-xs">
          <summary className="cursor-pointer text-muted-foreground">{t("labAdvancedOptionsSummary")}</summary>
          <div className="mt-2 space-y-3">
            {evalSync ? null : (
              <div className="flex flex-wrap gap-3 text-sm">
                <label className="flex items-center gap-1.5">
                  <input
                    type="radio"
                    name="follow-eval"
                    checked={evalFollowMode === "poll"}
                    onChange={() => setEvalFollowMode("poll")}
                    disabled={evalRunning}
                  />
                  {t("followModePoll")}
                </label>
                <label className="flex items-center gap-1.5">
                  <input
                    type="radio"
                    name="follow-eval"
                    checked={evalFollowMode === "sse"}
                    onChange={() => setEvalFollowMode("sse")}
                    disabled={evalRunning}
                  />
                  {t("followModeSse")}
                </label>
              </div>
            )}
            <p className="text-muted-foreground leading-relaxed">{t("labAdvancedClassifierJobHelp")}</p>
          </div>
        </details>
        <div className="grid gap-2">
          <Label htmlFor="emodel">{t("classifierEvalModelId")}</Label>
          <Input id="emodel" value={evalModelId} onChange={(e) => setEvalModelId(e.target.value)} />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="efile">{t("classifierEvalFile")}</Label>
          <Input
            id="efile"
            type="file"
            accept=".xlsx,.xls"
            onChange={(e) => setEvalFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" disabled={evalRunning || !classifierOk} onClick={() => void runEval()}>
            {evalRunning ? t("evalRunning") : t("classifierEvalSubmit")}
          </Button>
          {evalRunning ? (
            <Button type="button" variant="outline" onClick={() => evalAbortRef.current?.abort()}>
              {t("jobCancel")}
            </Button>
          ) : null}
        </div>
        {evalErr ? (
          <p className={`text-sm ${evalPollTimedOut ? "text-amber-700 dark:text-amber-400" : "text-destructive"}`}>
            {evalErr}
          </p>
        ) : null}
        {evalPollTimedOut && evalAccepted && !evalRunning ? (
          <Button type="button" variant="secondary" size="sm" onClick={() => void resumeClassifierEvalWatch()}>
            {t("jobResumeWatching")}
          </Button>
        ) : null}
        {!evalSync && (evalAccepted || evalStatus) ? (
          <LabJobPanel
            accepted={evalAccepted}
            taskStatus={evalStatus}
            queuedHint={!!evalAccepted && !evalStatus}
            stoppedWaiting={evalStoppedWaiting}
            watchElapsedSeconds={watchElapsedSeconds}
          />
        ) : null}
        {evalOut != null ? (
          <pre className="bg-muted/40 max-h-[240px] overflow-auto rounded-md border p-3 text-xs">
            {JSON.stringify(evalOut, null, 2)}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function LabClassifierClassifyPanel(props: Readonly<{ classifierOk: boolean }>) {
  const { classifierOk } = props;
  const t = useTranslations("Lab");

  const [clsQuery, setClsQuery] = useState("How many meetings?");
  const [clsModelId, setClsModelId] = useState("default");
  const [clsOut, setClsOut] = useState<unknown>(null);
  const [clsErr, setClsErr] = useState<string | null>(null);
  const [clsRunning, setClsRunning] = useState(false);

  async function runClassify() {
    setClsRunning(true);
    setClsErr(null);
    setClsOut(null);
    try {
      const data = await apiFetch<unknown>(apiProductPath("/lab/classifier/classify"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query: clsQuery, modelId: clsModelId }),
      });
      setClsOut(data);
    } catch {
      setClsErr(t("evalError"));
    } finally {
      setClsRunning(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("classifierClassifySubmit")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="grid gap-2">
          <Label htmlFor="q">{t("classifierQuery")}</Label>
          <Input id="q" value={clsQuery} onChange={(e) => setClsQuery(e.target.value)} />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="mid">{t("classifierModelIdField")}</Label>
          <Input id="mid" value={clsModelId} onChange={(e) => setClsModelId(e.target.value)} />
        </div>
        <Button type="button" disabled={clsRunning || !classifierOk} onClick={() => void runClassify()}>
          {clsRunning ? t("evalRunning") : t("classifierClassifySubmit")}
        </Button>
        {clsErr ? <p className="text-destructive text-sm">{clsErr}</p> : null}
        {clsOut != null ? (
          <pre className="bg-muted/40 max-h-[240px] overflow-auto rounded-md border p-3 text-xs">
            {JSON.stringify(clsOut, null, 2)}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}
