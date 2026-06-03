"use client";

import type { LabJobSectionKey } from "@/features/lab/lib/lab-job-persistence";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { fetchLabJobStatusOnce, sleep } from "@/lib/async-task";
import {
  eventToAsyncTaskStatus,
  LabSseConfigurationError,
  streamLabJobLive,
  type LabJobStreamCallbacks,
} from "@/lib/lab-job-sse";
import {
  EMPTY_LAB_PROGRESS_SNAPSHOT,
  mergeLabProgressSnapshot,
  type LabProgressSnapshot,
} from "@/features/lab/lib/lab-job-progress-payload";
import type { AsyncTaskStatusDto, LabJobAcceptedDto, LabJobEventDto, LabJobLiveConnectionState } from "@/types/api";
import { useCallback, useEffect, useRef, useState } from "react";

export type UseLabJobSseOptions = Readonly<{
  accepted: LabJobAcceptedDto | null;
  enabled?: boolean;
  onTick?: (status: AsyncTaskStatusDto) => void;
  onTerminal?: (status: AsyncTaskStatusDto) => void;
  onStreamError?: (error: unknown) => void;
}>;

/** Enough tail for long campaigns; reducer keeps latest global counters from events. */
const RECENT_JOB_EVENTS_MAX = 200;

const STRUCTURED_JOB_EVENT_TYPES = new Set([
  "ACCEPTED",
  "RAG_EVALUATION_ACCEPTED",
  "DATASET_RESOLVED",
  "KNOWLEDGE_BASE_CHECKED",
  "CAMPAIGN_ACCEPTED",
  "CAMPAIGN_PLANNED",
  "CAMPAIGN_STARTED",
  "RUN_STARTED",
  "PRESET_STARTED",
  "ITEM_STARTED",
  "SNAPSHOT_PREPARATION_STARTED",
  "SNAPSHOT_PREPARATION_COMPLETED",
  "ITEM_COMPLETED",
  "ITEM_FAILED",
  "ITEM_SKIPPED",
  "EXPORT_GENERATED",
  "RUN_COMPLETED",
  "CAMPAIGN_COMPLETED",
  "FAILED",
  "CANCELLED",
]);

const SPAM_JOB_MESSAGE = [
  /^Resolving typed dataset/i,
  /^Auto-reindex lock acquired/i,
  /^RAG dataset resolved:/i,
  /^Parsed dataset /i,
];

function shouldTrackJobEvent(event: LabJobEventDto): boolean {
  if (event.type === "HEARTBEAT" || event.type === "SNAPSHOT" || event.type === "PROGRESS") {
    return false;
  }
  const msg = event.message?.trim() ?? "";
  if (msg && SPAM_JOB_MESSAGE.some((re) => re.test(msg))) {
    return false;
  }
  return STRUCTURED_JOB_EVENT_TYPES.has(event.type);
}

export type UseLabJobSseResult = Readonly<{
  connectionState: LabJobLiveConnectionState;
  taskStatus: AsyncTaskStatusDto | null;
  lastEventId: number | null;
  recentEvents: LabJobEventDto[];
  progressSnapshot: LabProgressSnapshot;
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
/** Leave "Connecting…" if hydrate + stream open do not progress (never spin forever). */
const CONNECTING_TIMEOUT_MS = 8_000;

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

function isActiveJobStatus(status: string | null | undefined): boolean {
  const st = taskStatusUpper(status);
  return st === "RUNNING" || st === "QUEUED" || st === "CANCELLING" || st === "ACCEPTED";
}

function connectionStateForTerminalStatus(status: AsyncTaskStatusDto): LabJobLiveConnectionState {
  const st = taskStatusUpper(status.status);
  if (st === "SUCCEEDED") return "completed";
  if (st === "FAILED") return "failed";
  if (st === "CANCELLED" || st === "CANCELED") return "cancelled";
  return "failed";
}

function applyTerminalFromPoll(
  snapshot: AsyncTaskStatusDto,
  setters: {
    setTaskStatus: (s: AsyncTaskStatusDto) => void;
    setConnectionState: (s: LabJobLiveConnectionState) => void;
    onTick?: (s: AsyncTaskStatusDto) => void;
    onTerminal?: (s: AsyncTaskStatusDto) => void;
  },
  refs: { taskStatusRef: { current: AsyncTaskStatusDto | null } },
): void {
  refs.taskStatusRef.current = snapshot;
  setters.setTaskStatus(snapshot);
  setters.onTick?.(snapshot);
  setters.setConnectionState(connectionStateForTerminalStatus(snapshot));
  setters.onTerminal?.(snapshot);
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
  const [recentEvents, setRecentEvents] = useState<LabJobEventDto[]>([]);
  const [progressSnapshot, setProgressSnapshot] = useState<LabProgressSnapshot>(EMPTY_LAB_PROGRESS_SNAPSHOT);
  const [resumeNonce, setResumeNonce] = useState(0);

  const abortRef = useRef<AbortController | null>(null);
  const resumedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastEventIdRef = useRef<number | null>(null);
  const lastEventAtRef = useRef<number | null>(null);
  const everConnectedRef = useRef(false);
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
    queueMicrotask(() => {
      if (!cancelled) {
        setRecentEvents([]);
        setProgressSnapshot(EMPTY_LAB_PROGRESS_SNAPSHOT);
      }
    });
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
        if (!cancelled) {
          everConnectedRef.current = true;
          promoteToLive();
        }
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
        if (!cancelled && everConnectedRef.current && !hasRecentActivity()) {
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
        if (cancelled) return;
        if (event.type !== "SNAPSHOT" && event.eventId <= 0) return;
        markRecentActivity();
        promoteToLive();
        if (event.eventId > 0) {
          const next =
            lastEventIdRef.current == null ? event.eventId : Math.max(lastEventIdRef.current, event.eventId);
          lastEventIdRef.current = next;
          setLastEventId(next);
        }
        setProgressSnapshot((prev) => mergeLabProgressSnapshot(prev, event));
        if (shouldTrackJobEvent(event)) {
          setRecentEvents((prev) => {
            const tail = prev.length >= RECENT_JOB_EVENTS_MAX ? prev.slice(-(RECENT_JOB_EVENTS_MAX - 1)) : prev;
            return [...tail, event];
          });
        }
        const mapped = eventToAsyncTaskStatus(event, taskStatusRef.current);
        if (mapped) {
          taskStatusRef.current = mapped;
          setTaskStatus(mapped);
          callbacksRef.current.onTick?.(mapped);
        }
      },
    };

    const connectingTimer = globalThis.setTimeout(() => {
      void (async () => {
        if (cancelled || everConnectedRef.current) return;
        try {
          const fresh = await hydrateLabJobStatus(jobId, { signal: controller.signal });
          if (cancelled) return;
          taskStatusRef.current = fresh;
          setTaskStatus(fresh);
          callbacksRef.current.onTick?.(fresh);
          if (fresh.terminal) {
            applyTerminalFromPoll(
              fresh,
              {
                setTaskStatus,
                setConnectionState,
                onTick: callbacksRef.current.onTick,
                onTerminal: callbacksRef.current.onTerminal,
              },
              { taskStatusRef },
            );
            return;
          }
          if (isActiveJobStatus(fresh.status)) {
            setConnectionState("reconnecting");
            return;
          }
        } catch {
          /* fall through to configuration_error */
        }
        if (!cancelled && !everConnectedRef.current) {
          setConnectionState((prev) =>
            prev === "connecting" || prev === "reconnecting" ? "configuration_error" : prev,
          );
        }
      })();
    }, CONNECTING_TIMEOUT_MS);

    void (async () => {
      everConnectedRef.current = false;
      try {
        setConnectionState("connecting");
        const snapshot = await hydrateLabJobStatus(jobId, { signal: controller.signal });
        if (cancelled) return;
        taskStatusRef.current = snapshot;
        setTaskStatus(snapshot);
        callbacksRef.current.onTick?.(snapshot);
        if (!snapshot.terminal) {
          markRecentActivity();
        }
        if (snapshot.terminal) {
          applyTerminalFromPoll(
            snapshot,
            {
              setTaskStatus,
              setConnectionState,
              onTick: callbacksRef.current.onTick,
              onTerminal: callbacksRef.current.onTerminal,
            },
            { taskStatusRef },
          );
          return;
        }

        let streamAttempts = 0;
        while (!cancelled && !controller.signal.aborted) {
          try {
            const terminal = await streamLabJobLive(streamPath, {
              signal: controller.signal,
              sinceEventId: lastEventIdRef.current,
              callbacks,
            });
            if (cancelled) return;
            taskStatusRef.current = terminal;
            setTaskStatus(terminal);
            callbacksRef.current.onTick?.(terminal);
            setConnectionState(connectionStateForTerminalStatus(terminal));
            callbacksRef.current.onTerminal?.(terminal);
            return;
          } catch (e) {
            if (cancelled || (e instanceof DOMException && e.name === "AbortError")) {
              throw e;
            }
            if (e instanceof LabSseConfigurationError) {
              throw e;
            }
            const status =
              e instanceof Error && "status" in e
                ? Number((e as Error & { status?: number }).status)
                : typeof e === "object" && e != null && "status" in e
                  ? Number((e as { status?: number }).status)
                  : NaN;
            if (status === 404 || status === 401 || status === 403) {
              throw e;
            }

            let poll: AsyncTaskStatusDto;
            try {
              poll = await hydrateLabJobStatus(jobId, { signal: controller.signal });
            } catch {
              throw e;
            }
            if (cancelled) return;
            taskStatusRef.current = poll;
            setTaskStatus(poll);
            callbacksRef.current.onTick?.(poll);
            if (poll.terminal) {
              applyTerminalFromPoll(
                poll,
                {
                  setTaskStatus,
                  setConnectionState,
                  onTick: callbacksRef.current.onTick,
                  onTerminal: callbacksRef.current.onTerminal,
                },
                { taskStatusRef },
              );
              return;
            }
            if (isActiveJobStatus(poll.status)) {
              setConnectionState("reconnecting");
              streamAttempts += 1;
              if (streamAttempts > 8) {
                throw new Error("Live stream stalled while job is still running on the server.");
              }
              await sleep(1_000);
              continue;
            }
            throw e;
          }
        }
      } catch (e) {
        if (cancelled || (e instanceof DOMException && e.name === "AbortError")) {
          if (!cancelled) setConnectionState("idle");
          return;
        }
        callbacksRef.current.onStreamError?.(e);
        if (!cancelled) {
          if (e instanceof LabSseConfigurationError) {
            setConnectionState("configuration_error");
            return;
          }
          const status =
            e instanceof Error && "status" in e
              ? Number((e as Error & { status?: number }).status)
              : typeof e === "object" && e != null && "status" in e
                ? Number((e as { status?: number }).status)
                : NaN;
          if (status === 404 || status === 401 || status === 403) {
            setConnectionState("configuration_error");
            return;
          }
          if (everConnectedRef.current && !hasRecentActivity()) {
            setConnectionState("reconnecting");
          } else {
            setConnectionState("configuration_error");
          }
        }
      } finally {
        globalThis.clearTimeout(connectingTimer);
      }
    })();

    return () => {
      cancelled = true;
      globalThis.clearTimeout(connectingTimer);
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
    recentEvents,
    progressSnapshot,
    resume,
    stop,
  };
}

/** @deprecated Use {@link useLabJobSse}. */
export const useLabJobLiveEvents = useLabJobSse;
