"use client";

import type { LabJobSectionKey } from "@/features/lab/lib/lab-job-persistence";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { fetchLabJobStatusOnce } from "@/lib/async-task";
import {
  eventToAsyncTaskStatus,
  streamLabJobLive,
  type LabJobStreamCallbacks,
} from "@/lib/lab-job-sse";
import type { AsyncTaskStatusDto, LabJobAcceptedDto, LabJobEventDto, LabJobLiveConnectionState } from "@/types/api";
import { useCallback, useEffect, useRef, useState } from "react";

export type UseLabJobSseOptions = Readonly<{
  accepted: LabJobAcceptedDto | null;
  enabled?: boolean;
  onTick?: (status: AsyncTaskStatusDto) => void;
  onTerminal?: (status: AsyncTaskStatusDto) => void;
  onStreamError?: (error: unknown) => void;
}>;

export type UseLabJobSseResult = Readonly<{
  connectionState: LabJobLiveConnectionState;
  taskStatus: AsyncTaskStatusDto | null;
  lastEventId: number | null;
  resume: () => void;
  stop: () => void;
}>;

/** @deprecated Use {@link UseLabJobSseOptions}. */
export type UseLabJobLiveEventsOptions = UseLabJobSseOptions;

/** @deprecated Use {@link UseLabJobSseResult}. */
export type UseLabJobLiveEventsResult = UseLabJobSseResult;

const RESUMED_FLASH_MS = 2_500;
/** Recent SSE activity suppresses reconnecting UI. */
const RECENT_EVENT_MS = 3_000;

function taskStatusUpper(status: string | null | undefined): string {
  return (status ?? "").trim().toUpperCase();
}

function isTerminalConnectionState(state: LabJobLiveConnectionState): boolean {
  return (
    state === "completed" ||
    state === "failed" ||
    state === "cancelled" ||
    state === "finished_away"
  );
}

/**
 * One-shot GET to hydrate job state before (re)opening the SSE stream.
 */
export async function hydrateLabJobStatus(
  jobId: string,
  options?: { signal?: AbortSignal },
): Promise<AsyncTaskStatusDto> {
  return fetchLabJobStatusOnce(jobId, { signal: options?.signal });
}

/**
 * Request in-tab resume for a persisted Lab job (session store + evaluation card effect).
 */
export function resumeLabJob(sectionKey: LabJobSectionKey, jobId: string): void {
  useLabJobSessionStore.getState().requestResumeLabJob(sectionKey, jobId);
}

/**
 * Canonical Lab job progress: one-shot GET on attach, then SSE-only with reconnect/backoff.
 */
export function useLabJobSse(options: UseLabJobSseOptions): UseLabJobSseResult {
  const { accepted, enabled = true, onTick, onTerminal, onStreamError } = options;
  const [connectionState, setConnectionState] = useState<LabJobLiveConnectionState>("idle");
  const [taskStatus, setTaskStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [lastEventId, setLastEventId] = useState<number | null>(null);
  const [resumeNonce, setResumeNonce] = useState(0);

  const abortRef = useRef<AbortController | null>(null);
  const resumedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastEventIdRef = useRef<number | null>(null);
  const lastEventAtRef = useRef<number | null>(null);
  const taskStatusRef = useRef<AsyncTaskStatusDto | null>(null);
  const callbacksRef = useRef({ onTick, onTerminal, onStreamError });

  const abortStreamOnly = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    if (resumedTimerRef.current) {
      clearTimeout(resumedTimerRef.current);
      resumedTimerRef.current = null;
    }
  }, []);

  const markRecentActivity = useCallback(() => {
    lastEventAtRef.current = Date.now();
  }, []);

  const hasRecentActivity = useCallback(() => {
    const last = lastEventAtRef.current;
    if (last == null) return false;
    return Date.now() - last <= RECENT_EVENT_MS;
  }, []);

  const promoteToLive = useCallback(() => {
    setConnectionState((prev) => {
      if (isTerminalConnectionState(prev)) return prev;
      return "live";
    });
  }, []);

  const stop = useCallback(() => {
    abortStreamOnly();
    setConnectionState("idle");
  }, [abortStreamOnly]);

  const resume = useCallback(() => {
    setResumeNonce((n) => n + 1);
  }, []);

  useEffect(() => {
    taskStatusRef.current = taskStatus;
  }, [taskStatus]);

  useEffect(() => {
    lastEventIdRef.current = lastEventId;
  }, [lastEventId]);

  useEffect(() => {
    callbacksRef.current = { onTick, onTerminal, onStreamError };
  }, [onTick, onTerminal, onStreamError]);

  const streamActive = enabled && Boolean(accepted?.jobId?.trim());

  useEffect(() => {
    if (!streamActive) {
      abortStreamOnly();
      return;
    }

    const jobId = accepted?.jobId?.trim();
    const streamPath = accepted?.streamPath?.trim();
    if (!jobId || !streamPath) {
      abortStreamOnly();
      return;
    }

    let cancelled = false;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    const callbacks: LabJobStreamCallbacks = {
      onConnecting: () => {
        if (!cancelled) {
          setConnectionState((prev) => {
            if (prev === "live" || prev === "resumed") {
              return hasRecentActivity() ? "live" : "reconnecting";
            }
            return "connecting";
          });
        }
      },
      onLive: () => {
        if (!cancelled) promoteToLive();
      },
      onResumed: () => {
        if (!cancelled) {
          markRecentActivity();
          setConnectionState("resumed");
          if (resumedTimerRef.current) clearTimeout(resumedTimerRef.current);
          resumedTimerRef.current = setTimeout(() => {
            if (!cancelled) setConnectionState((prev) => (prev === "resumed" ? "live" : prev));
          }, RESUMED_FLASH_MS);
        }
      },
      onReconnecting: () => {
        if (!cancelled && !hasRecentActivity()) {
          setConnectionState("reconnecting");
        }
      },
      onTaskTick: (status) => {
        if (cancelled) return;
        markRecentActivity();
        promoteToLive();
        taskStatusRef.current = status;
        setTaskStatus(status);
        callbacksRef.current.onTick?.(status);
      },
      onJobEvent: (event: LabJobEventDto) => {
        if (cancelled || event.eventId <= 0) return;
        markRecentActivity();
        promoteToLive();
        const next = lastEventIdRef.current == null ? event.eventId : Math.max(lastEventIdRef.current, event.eventId);
        lastEventIdRef.current = next;
        setLastEventId(next);
        const mapped = eventToAsyncTaskStatus(event, taskStatusRef.current);
        if (mapped) {
          taskStatusRef.current = mapped;
          setTaskStatus(mapped);
          callbacksRef.current.onTick?.(mapped);
        }
      },
    };

    void (async () => {
      try {
        setConnectionState("connecting");
        const snapshot = await hydrateLabJobStatus(jobId, { signal: controller.signal });
        if (cancelled) return;
        taskStatusRef.current = snapshot;
        setTaskStatus(snapshot);
        callbacksRef.current.onTick?.(snapshot);
        if (snapshot.terminal) {
          const st = taskStatusUpper(snapshot.status);
          if (st === "SUCCEEDED") setConnectionState("completed");
          else if (st === "FAILED") setConnectionState("failed");
          else if (st === "CANCELLED" || st === "CANCELED") setConnectionState("cancelled");
          else setConnectionState("failed");
          callbacksRef.current.onTerminal?.(snapshot);
          return;
        }

        const terminal = await streamLabJobLive(streamPath, {
          signal: controller.signal,
          sinceEventId: lastEventIdRef.current,
          callbacks,
        });
        if (cancelled) return;
        taskStatusRef.current = terminal;
        setTaskStatus(terminal);
        callbacksRef.current.onTick?.(terminal);
        const st = taskStatusUpper(terminal.status);
        if (st === "SUCCEEDED") setConnectionState("completed");
        else if (st === "FAILED") setConnectionState("failed");
        else if (st === "CANCELLED" || st === "CANCELED") setConnectionState("cancelled");
        else setConnectionState("failed");
        callbacksRef.current.onTerminal?.(terminal);
      } catch (e) {
        if (cancelled || (e instanceof DOMException && e.name === "AbortError")) {
          if (!cancelled) setConnectionState("idle");
          return;
        }
        callbacksRef.current.onStreamError?.(e);
        if (!cancelled && !hasRecentActivity()) {
          setConnectionState("reconnecting");
        }
      }
    })();

    return () => {
      cancelled = true;
      abortStreamOnly();
    };
  }, [
    accepted?.jobId,
    accepted?.streamPath,
    abortStreamOnly,
    hasRecentActivity,
    markRecentActivity,
    promoteToLive,
    resumeNonce,
    streamActive,
  ]);

  const connectionStateOut: LabJobLiveConnectionState = streamActive ? connectionState : "idle";

  return {
    connectionState: connectionStateOut,
    taskStatus,
    lastEventId,
    resume,
    stop,
  };
}

/** @deprecated Use {@link useLabJobSse}. */
export const useLabJobLiveEvents = useLabJobSse;
