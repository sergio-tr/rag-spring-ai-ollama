import { pollLabJob, sleep } from "@/lib/async-task";
import { getApiBaseUrl, sanitizePlainErrorTextForUi } from "@/lib/api-client";
import { getAccessToken } from "@/lib/access-token";
import { createTraceparent } from "@/lib/traceparent";
import type { AsyncTaskStatusDto, LabJobEventDto } from "@/types/api";

export type LabJobStreamCallbacks = {
  onConnecting?: () => void;
  onLive?: () => void;
  onResumed?: () => void;
  onReconnecting?: () => void;
  onTaskTick?: (status: AsyncTaskStatusDto) => void;
  onJobEvent?: (event: LabJobEventDto) => void;
  onFinishedAway?: (status: AsyncTaskStatusDto) => void;
};

const MAX_SSE_RETRIES = 8;
const SSE_RETRY_BASE_MS = 800;

function toAbsoluteUrl(pathOrUrl: string): string {
  if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
    return pathOrUrl;
  }
  const base = getApiBaseUrl();
  const p = pathOrUrl.startsWith("/") ? pathOrUrl : `/${pathOrUrl}`;
  return `${base}${p}`;
}

function buildStreamUrl(streamPath: string, sinceEventId: number | null | undefined): string {
  const url = new URL(toAbsoluteUrl(streamPath));
  if (sinceEventId != null && sinceEventId > 0) {
    url.searchParams.set("since", String(sinceEventId));
  }
  return url.toString();
}

function parseAsyncTaskStatusLine(raw: string, currentEvent: string): AsyncTaskStatusDto | null {
  const isTaskEvent = currentEvent === "task" || currentEvent === "";
  if (!isTaskEvent && !raw.startsWith("{")) {
    return null;
  }
  return JSON.parse(raw) as AsyncTaskStatusDto;
}

function parseJobEventLine(raw: string, currentEvent: string): LabJobEventDto | null {
  if (currentEvent !== "job-event" && currentEvent !== "heartbeat" && !raw.startsWith("{")) {
    return null;
  }
  const parsed = JSON.parse(raw) as LabJobEventDto;
  if (currentEvent === "heartbeat" || parsed.type === "HEARTBEAT") {
    return parsed;
  }
  return parsed;
}

function finishOrThrowOnFailedTerminal(dto: AsyncTaskStatusDto): AsyncTaskStatusDto | null {
  if (!dto.terminal) {
    return null;
  }
  if (dto.status === "FAILED") {
    const rawMsg = dto.errorMessage ?? "Job failed";
    const safe = sanitizePlainErrorTextForUi(rawMsg, 280) || "Job failed";
    throw new Error(safe);
  }
  return dto;
}

export function eventToAsyncTaskStatus(
  event: LabJobEventDto,
  previous: AsyncTaskStatusDto | null,
): AsyncTaskStatusDto | null {
  if (event.type === "HEARTBEAT" || event.eventId <= 0) {
    return null;
  }
  const payload = event.payload ?? {};
  const terminal =
    event.type === "COMPLETED" ||
    event.type === "FAILED" ||
    event.type === "CANCELLED" ||
    payload.terminal === true;
  const status =
    event.status ??
    (event.type === "COMPLETED"
      ? "SUCCEEDED"
      : event.type === "FAILED"
        ? "FAILED"
        : event.type === "CANCELLED"
          ? "CANCELLED"
          : previous?.status ?? "RUNNING");
  return {
    id: event.jobId,
    taskType: previous?.taskType ?? "LAB",
    status,
    progressText: event.progress ?? previous?.progressText ?? null,
    result: (payload.result as Record<string, unknown> | undefined) ?? previous?.result ?? null,
    errorMessage: (payload.errorMessage as string | undefined) ?? previous?.errorMessage ?? null,
    terminal,
    createdAt: previous?.createdAt ?? event.timestamp,
    updatedAt: event.timestamp,
    startedAt: previous?.startedAt ?? null,
    completedAt: terminal ? event.timestamp : previous?.completedAt ?? null,
    failureCode: (payload.failureCode as string | undefined) ?? previous?.failureCode ?? null,
  };
}

function extractJobIdFromStreamPath(streamPath: string): string | null {
  const match = streamPath.match(/\/lab\/jobs\/([^/]+)\/events/);
  return match?.[1] ?? null;
}

async function consumeSseStream(
  streamPath: string,
  options: {
    signal?: AbortSignal;
    sinceEventId?: number | null;
    callbacks?: LabJobStreamCallbacks;
  },
): Promise<AsyncTaskStatusDto> {
  const url = buildStreamUrl(streamPath, options.sinceEventId);
  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    traceparent: createTraceparent(),
  };
  const bearer = getAccessToken();
  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`;
  }

  options.callbacks?.onConnecting?.();

  const res = await fetch(url, {
    credentials: "include",
    headers,
    signal: options.signal,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || res.statusText);
  }

  const reader = res.body?.getReader();
  if (!reader) {
    throw new Error("No response body");
  }

  const decoder = new TextDecoder();
  let buffer = "";
  let currentEvent = "";
  let lastEventId = options.sinceEventId ?? 0;
  let sawReplay = false;
  let lastStatus: AsyncTaskStatusDto | null = null;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? "";

      for (const line of lines) {
        if (line.trim() === "") {
          currentEvent = "";
          continue;
        }
        if (line.startsWith("event:")) {
          currentEvent = line.slice(6).trim();
          continue;
        }
        if (line.startsWith("id:")) {
          const id = Number.parseInt(line.slice(3).trim(), 10);
          if (Number.isFinite(id) && id > lastEventId) {
            lastEventId = id;
          }
          continue;
        }
        if (line.startsWith("data:")) {
          const raw = line.slice(5).trim();
          if (raw === "" || raw === "[DONE]") {
            continue;
          }
          try {
            if (currentEvent === "job-event" || currentEvent === "heartbeat") {
              const event = parseJobEventLine(raw, currentEvent);
              if (event == null) continue;
              if (event.type === "HEARTBEAT") {
                options.callbacks?.onLive?.();
                continue;
              }
              if (event.eventId > 0) {
                if (event.eventId <= (options.sinceEventId ?? 0)) {
                  sawReplay = true;
                }
                lastEventId = Math.max(lastEventId, event.eventId);
                options.callbacks?.onJobEvent?.(event);
                const mapped = eventToAsyncTaskStatus(event, lastStatus);
                if (mapped) {
                  lastStatus = mapped;
                  options.callbacks?.onTaskTick?.(mapped);
                  const finished = finishOrThrowOnFailedTerminal(mapped);
                  if (finished !== null) {
                    return finished;
                  }
                }
              }
              continue;
            }

            const dto = parseAsyncTaskStatusLine(raw, currentEvent);
            if (dto === null) {
              continue;
            }
            lastStatus = dto;
            options.callbacks?.onTaskTick?.(dto);
            options.callbacks?.onLive?.();
            const finished = finishOrThrowOnFailedTerminal(dto);
            if (finished !== null) {
              return finished;
            }
          } catch (e) {
            if (e instanceof SyntaxError) {
              continue;
            }
            throw e;
          }
        }
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
  }

  if (sawReplay) {
    options.callbacks?.onResumed?.();
  }

  throw new Error("SSE stream ended before job completed");
}

/**
 * Subscribe to GET {product}/lab/jobs/{id}/events (SSE) with Bearer auth.
 */
export async function streamLabJob(
  streamPath: string,
  onTick: (s: AsyncTaskStatusDto) => void,
  options?: { signal?: AbortSignal; sinceEventId?: number | null },
): Promise<AsyncTaskStatusDto> {
  return consumeSseStream(streamPath, {
    signal: options?.signal,
    sinceEventId: options?.sinceEventId,
    callbacks: { onTaskTick: onTick, onLive: () => {} },
  });
}

/**
 * SSE with auto reconnect/backoff and internal poll fallback when the stream cannot be restored.
 */
export async function streamLabJobLive(
  streamPath: string,
  options: {
    signal?: AbortSignal;
    sinceEventId?: number | null;
    callbacks?: LabJobStreamCallbacks;
  },
): Promise<AsyncTaskStatusDto> {
  let attempt = 0;
  let since = options.sinceEventId ?? null;
  let lastStatus: AsyncTaskStatusDto | null = null;

  while (attempt <= MAX_SSE_RETRIES) {
    if (options.signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    try {
      if (attempt > 0) {
        options.callbacks?.onReconnecting?.();
      }
      const terminal = await consumeSseStream(streamPath, {
        signal: options.signal,
        sinceEventId: since,
        callbacks: {
          ...options.callbacks,
          onTaskTick: (status) => {
            lastStatus = status;
            options.callbacks?.onTaskTick?.(status);
          },
          onJobEvent: (event) => {
            if (event.eventId > 0) {
              since = since == null ? event.eventId : Math.max(since, event.eventId);
            }
            options.callbacks?.onJobEvent?.(event);
          },
        },
      });
      return terminal;
    } catch (e) {
      if (options.signal?.aborted || (e instanceof DOMException && e.name === "AbortError")) {
        throw e;
      }
      attempt += 1;
      if (attempt > MAX_SSE_RETRIES) {
        break;
      }
      await sleep(SSE_RETRY_BASE_MS * attempt);
    }
  }

  const jobId = extractJobIdFromStreamPath(streamPath);
  if (!jobId) {
    throw new Error("Live stream unavailable");
  }

  options.callbacks?.onReconnecting?.();
  return pollLabJob(
    jobId,
    (status) => {
      lastStatus = status;
      options.callbacks?.onTaskTick?.(status);
    },
    { signal: options.signal, throwOnFailed: true },
  ).then((terminal) => {
    if (lastStatus && !options.signal?.aborted) {
      options.callbacks?.onFinishedAway?.(terminal);
    }
    return terminal;
  });
}
