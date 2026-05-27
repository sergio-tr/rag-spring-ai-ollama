import { sleep } from "@/lib/async-task";
import {
  apiProductPath,
  getRagApiProductPrefix,
  resolveLabJobApiUrl,
  sanitizePlainErrorTextForUi,
} from "@/lib/api-client";
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
};

const SSE_RETRY_BASE_MS = 800;
const SSE_RETRY_MAX_MS = 15_000;

const LAB_SSE_HTML_MISROUTE_MESSAGE =
  "Live updates reached the web application instead of the backend API. Check NEXT_PUBLIC_API_BASE_URL or dev proxy.";

/** SSE response was HTML/404 or not `text/event-stream` (misconfigured URL or proxy). */
export class LabSseConfigurationError extends Error {
  readonly code = "LAB_SSE_CONFIGURATION";
  status?: number;

  constructor(message = LAB_SSE_HTML_MISROUTE_MESSAGE, status?: number) {
    super(message);
    this.name = "LabSseConfigurationError";
    this.status = status;
  }
}

function isLabSseDebugEnabled(): boolean {
  if (typeof window === "undefined") {
    return process.env.NEXT_PUBLIC_DEBUG_LAB_SSE === "1";
  }
  try {
    return (
      process.env.NEXT_PUBLIC_DEBUG_LAB_SSE === "1" ||
      window.sessionStorage?.getItem("lab-sse-debug") === "1"
    );
  } catch {
    return process.env.NEXT_PUBLIC_DEBUG_LAB_SSE === "1";
  }
}

function labSseDebug(message: string, detail?: Record<string, unknown>): void {
  if (!isLabSseDebugEnabled()) return;
  if (detail) {
    console.info(`[LAB SSE] ${message}`, detail);
  } else {
    console.info(`[LAB SSE] ${message}`);
  }
}

function responseLooksLikeHtml(contentType: string, bodyPreview: string): boolean {
  const ct = contentType.toLowerCase();
  if (ct.includes("text/html")) {
    return true;
  }
  const trimmed = bodyPreview.trimStart().toLowerCase();
  return trimmed.startsWith("<!doctype") || trimmed.startsWith("<html");
}

function assertEventStreamResponse(res: Response, bodyPreview = ""): void {
  const contentType = res.headers.get("content-type") ?? "";
  if (!res.ok) {
    if (responseLooksLikeHtml(contentType, bodyPreview) || res.status === 404) {
      throw new LabSseConfigurationError(LAB_SSE_HTML_MISROUTE_MESSAGE, res.status);
    }
    return;
  }
  if (
    responseLooksLikeHtml(contentType, bodyPreview) ||
    (!contentType.toLowerCase().includes("text/event-stream") && contentType !== "")
  ) {
    throw new LabSseConfigurationError(LAB_SSE_HTML_MISROUTE_MESSAGE, res.status);
  }
}

function isNonRetryableStreamError(e: unknown): boolean {
  if (e instanceof LabSseConfigurationError) return true;
  if (e instanceof DOMException && e.name === "AbortError") return true;
  const status =
    e instanceof Error && "status" in e
      ? Number((e as Error & { status?: number }).status)
      : typeof e === "object" && e != null && "status" in e
        ? Number((e as { status?: number }).status)
        : NaN;
  return status === 401 || status === 403 || status === 404;
}

/** Normalize backend poll/stream paths to a full browser URL with the product API prefix. */
export function toAbsoluteLabJobStreamUrl(pathOrUrl: string): string {
  if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
    return pathOrUrl;
  }
  const p = pathOrUrl.startsWith("/") ? pathOrUrl : `/${pathOrUrl}`;
  const prefix = getRagApiProductPrefix();
  const productPath = p.startsWith(prefix) ? p : p.startsWith("/lab/") ? `${prefix}${p}` : apiProductPath(p);
  return resolveLabJobApiUrl(productPath);
}

function buildStreamUrl(streamPath: string, sinceEventId: number | null | undefined): string {
  const absolute = toAbsoluteLabJobStreamUrl(streamPath);
  const url =
    absolute.startsWith("http://") || absolute.startsWith("https://")
      ? new URL(absolute)
      : typeof window !== "undefined"
        ? new URL(absolute, window.location.origin)
        : new URL(absolute, "http://127.0.0.1:3000");
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
  if (event.type === "HEARTBEAT") {
    return null;
  }
  if (
    event.type === "RAG_EVALUATION_ACCEPTED" ||
    event.type === "SNAPSHOT_PREPARATION_STARTED" ||
    event.type === "SNAPSHOT_PREPARATION_COMPLETED"
  ) {
    const progress =
      event.progress?.trim() ||
      event.message?.trim() ||
      previous?.progressText ||
      null;
    return {
      id: event.jobId,
      taskType: previous?.taskType ?? "LAB",
      status: "RUNNING",
      progressText: progress,
      result: previous?.result ?? null,
      errorMessage: previous?.errorMessage ?? null,
      terminal: false,
      createdAt: previous?.createdAt ?? event.timestamp,
      updatedAt: event.timestamp,
      startedAt: previous?.startedAt ?? event.timestamp,
      completedAt: previous?.completedAt ?? null,
      failureCode: previous?.failureCode ?? null,
    };
  }
  if (event.type === "SNAPSHOT") {
    const status = event.status ?? previous?.status ?? "QUEUED";
    const st = status.trim().toUpperCase();
    const terminal =
      st === "SUCCEEDED" ||
      st === "FAILED" ||
      st === "CANCELLED" ||
      st === "CANCELED" ||
      (event.payload?.terminal === true);
    return {
      id: event.jobId,
      taskType: previous?.taskType ?? "LAB",
      status,
      progressText: event.progress ?? previous?.progressText ?? null,
      result: previous?.result ?? null,
      errorMessage: previous?.errorMessage ?? null,
      terminal,
      createdAt: previous?.createdAt ?? event.timestamp,
      updatedAt: event.timestamp,
      startedAt: previous?.startedAt ?? null,
      completedAt: terminal ? event.timestamp : previous?.completedAt ?? null,
      failureCode: previous?.failureCode ?? null,
    };
  }
  if (event.eventId <= 0) {
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

async function consumeSseStream(
  streamPath: string,
  options: {
    signal?: AbortSignal;
    sinceEventId?: number | null;
    callbacks?: LabJobStreamCallbacks;
  },
): Promise<AsyncTaskStatusDto> {
  const url = buildStreamUrl(streamPath, options.sinceEventId);
  labSseDebug("resolved URL", { url });
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
  const contentType = res.headers.get("content-type") ?? "";
  labSseDebug("response", { status: res.status, contentType, url });
  let bodyPreview = "";
  if (!res.ok || !contentType.toLowerCase().includes("text/event-stream")) {
    bodyPreview = await res.clone().text().catch(() => "");
    assertEventStreamResponse(res, bodyPreview);
  }
  if (!res.ok) {
    const err = new Error(bodyPreview || res.statusText) as Error & { status?: number };
    err.status = res.status;
    throw err;
  }

  options.callbacks?.onLive?.();

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
              if (event.type === "SNAPSHOT") {
                labSseDebug("first event", { type: event.type, jobId: event.jobId });
                options.callbacks?.onLive?.();
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
                continue;
              }
              if (event.eventId > 0) {
                if (event.eventId <= (options.sinceEventId ?? 0)) {
                  sawReplay = true;
                }
                lastEventId = Math.max(lastEventId, event.eventId);
                options.callbacks?.onLive?.();
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

function sseRetryDelayMs(attempt: number): number {
  return Math.min(SSE_RETRY_BASE_MS * Math.max(attempt, 1), SSE_RETRY_MAX_MS);
}

/**
 * SSE with auto reconnect/backoff until the job reaches a terminal state or the signal aborts.
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
  let everConnected = false;

  while (true) {
    if (options.signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    try {
      if (attempt > 0 && everConnected) {
        options.callbacks?.onReconnecting?.();
      }
      const result = await consumeSseStream(streamPath, {
        signal: options.signal,
        sinceEventId: since,
        callbacks: {
          ...options.callbacks,
          onLive: () => {
            everConnected = true;
            options.callbacks?.onLive?.();
          },
          onJobEvent: (event) => {
            if (event.eventId > 0) {
              since = since == null ? event.eventId : Math.max(since, event.eventId);
            }
            options.callbacks?.onJobEvent?.(event);
          },
        },
      });
      everConnected = true;
      return result;
    } catch (e) {
      if (options.signal?.aborted || (e instanceof DOMException && e.name === "AbortError")) {
        throw e;
      }
      if (isNonRetryableStreamError(e)) {
        throw e;
      }
      attempt += 1;
      await sleep(sseRetryDelayMs(attempt));
    }
  }
}
