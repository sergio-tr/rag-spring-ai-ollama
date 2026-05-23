"use client";

import { fetchLabJobStatusOnce } from "@/lib/async-task";
import {
  eventToAsyncTaskStatus,
  streamLabJobLive,
  type LabJobStreamCallbacks,
} from "@/lib/lab-job-sse";
import type { AsyncTaskStatusDto, LabJobAcceptedDto, LabJobEventDto, LabJobLiveConnectionState } from "@/types/api";
import { useCallback, useEffect, useRef, useState } from "react";

export type UseLabJobLiveEventsOptions = Readonly<{
  accepted: LabJobAcceptedDto | null;
  enabled?: boolean;
  onTick?: (status: AsyncTaskStatusDto) => void;
  onTerminal?: (status: AsyncTaskStatusDto) => void;
  onStreamError?: (error: unknown) => void;
}>;

export type UseLabJobLiveEventsResult = Readonly<{
  connectionState: LabJobLiveConnectionState;
  taskStatus: AsyncTaskStatusDto | null;
  lastEventId: number | null;
  resume: () => void;
  stop: () => void;
}>;

const RESUMED_FLASH_MS = 2_500;

function taskStatusUpper(status: string | null | undefined): string {
  return (status ?? "").trim().toUpperCase();
}

/**
 * Canonical Lab job live stream: SSE by default, auto reconnect with backoff,
 * resume via persisted backend events (`?since=eventId`), poll as internal fallback only.
 */
export function useLabJobLiveEvents(options: UseLabJobLiveEventsOptions): UseLabJobLiveEventsResult {
  const { accepted, enabled = true, onTick, onTerminal, onStreamError } = options;
  const [connectionState, setConnectionState] = useState<LabJobLiveConnectionState>("idle");
  const [taskStatus, setTaskStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [lastEventId, setLastEventId] = useState<number | null>(null);
  const [resumeNonce, setResumeNonce] = useState(0);

  const abortRef = useRef<AbortController | null>(null);
  const resumedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastEventIdRef = useRef<number | null>(null);
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
          setConnectionState((prev) => (prev === "live" || prev === "resumed" ? "reconnecting" : "connecting"));
        }
      },
      onLive: () => {
        if (!cancelled) setConnectionState("live");
      },
      onResumed: () => {
        if (!cancelled) {
          setConnectionState("resumed");
          if (resumedTimerRef.current) clearTimeout(resumedTimerRef.current);
          resumedTimerRef.current = setTimeout(() => {
            if (!cancelled) setConnectionState((prev) => (prev === "resumed" ? "live" : prev));
          }, RESUMED_FLASH_MS);
        }
      },
      onReconnecting: () => {
        if (!cancelled) setConnectionState("reconnecting");
      },
      onTaskTick: (status) => {
        if (cancelled) return;
        taskStatusRef.current = status;
        setTaskStatus(status);
        callbacksRef.current.onTick?.(status);
      },
      onJobEvent: (event: LabJobEventDto) => {
        if (cancelled || event.eventId <= 0) return;
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
      onFinishedAway: (status) => {
        if (cancelled) return;
        taskStatusRef.current = status;
        setTaskStatus(status);
        setConnectionState("finished_away");
        callbacksRef.current.onTerminal?.(status);
      },
    };

    void (async () => {
      try {
        setConnectionState("connecting");
        const snapshot = await fetchLabJobStatusOnce(jobId, { signal: controller.signal });
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
        if (!cancelled) setConnectionState("reconnecting");
      }
    })();

    return () => {
      cancelled = true;
      abortStreamOnly();
    };
  }, [accepted?.jobId, accepted?.streamPath, abortStreamOnly, resumeNonce, streamActive]);

  const connectionStateOut: LabJobLiveConnectionState = streamActive ? connectionState : "idle";

  return {
    connectionState: connectionStateOut,
    taskStatus,
    lastEventId,
    resume,
    stop,
  };
}
