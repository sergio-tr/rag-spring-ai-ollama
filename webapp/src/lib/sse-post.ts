import { getApiBaseUrl, tryRefreshAccessToken } from "@/lib/api-client";
import { getAccessToken } from "@/lib/access-token";
import { createTraceparent } from "@/lib/traceparent";
import type { StreamDonePayload } from "@/types/api";

export type SsePostHandlers = {
  onDelta?: (text: string) => void;
  onDone?: (payload: StreamDonePayload) => void;
  onError?: (message: string, code?: string) => void;
};

export type SsePostHandlersExtended = SsePostHandlers & {
  /** Called when the request is aborted (user stop or new send); not a server error. */
  onAbort?: () => void;
};

/**
 * POST + SSE (text/event-stream): parses Spring SseEmitter frames (event + data lines).
 *
 * Lifecycle: pass an `AbortSignal` to cancel the fetch and the body reader. There is **no** automatic
 * retry (avoids duplicate assistant messages). Use {@link createTraceparent} via the `traceparent` header for OTEL correlation.
 */
export async function postSseJson(
  path: string,
  body: unknown,
  signal: AbortSignal | undefined,
  handlers: SsePostHandlersExtended,
): Promise<void> {
  const base = getApiBaseUrl();
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const url = path.startsWith("http") ? path : `${base}${normalizedPath}`;

  function authHeaders(): Record<string, string> {
    const h: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      traceparent: createTraceparent(),
    };
    const bearer = getAccessToken();
    if (bearer) {
      h.Authorization = `Bearer ${bearer}`;
    }
    return h;
  }

  if (signal?.aborted) {
    handlers.onAbort?.();
    return;
  }

  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  const onAbort = () => {
    void reader?.cancel().catch(() => {});
  };
  if (signal) {
    signal.addEventListener("abort", onAbort, { once: true });
  }

  try {
    let res: Response;
    try {
      res = await fetch(url, {
        method: "POST",
        credentials: "include",
        headers: authHeaders(),
        body: JSON.stringify(body),
        signal,
      });
    } catch (e) {
      if (signal?.aborted || isAbortError(e)) {
        handlers.onAbort?.();
        return;
      }
      handlers.onError?.(e instanceof Error ? e.message : String(e), "NETWORK");
      return;
    }

    if (res.status === 401) {
      const refreshed = await tryRefreshAccessToken();
      if (refreshed) {
        try {
          res = await fetch(url, {
            method: "POST",
            credentials: "include",
            headers: authHeaders(),
            body: JSON.stringify(body),
            signal,
          });
        } catch (e) {
          if (signal?.aborted || isAbortError(e)) {
            handlers.onAbort?.();
            return;
          }
          handlers.onError?.(e instanceof Error ? e.message : String(e), "NETWORK");
          return;
        }
      }
    }

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      handlers.onError?.(text || res.statusText, String(res.status));
      return;
    }

    reader = res.body?.getReader();
    if (!reader) {
      handlers.onError?.("No response body");
      return;
    }

    const decoder = new TextDecoder();
    try {
      await readSseEventStream(reader, decoder, signal, handlers);
    } finally {
      await reader.cancel().catch(() => {});
    }
  } finally {
    if (signal) {
      signal.removeEventListener("abort", onAbort);
    }
  }
}

type SseLineCtx = { currentEvent: string };

function handleSseLine(line: string, ctx: SseLineCtx, handlers: SsePostHandlersExtended): void {
  if (line.trim() === "") {
    ctx.currentEvent = "";
    return;
  }
  if (line.startsWith("event:")) {
    ctx.currentEvent = line.slice(6).trim();
    return;
  }
  if (!line.startsWith("data:")) {
    return;
  }
  const raw = line.slice(5).trim();
  if (raw === "[DONE]") {
    ctx.currentEvent = "";
    return;
  }
  dispatchSsePayload(raw, ctx.currentEvent, handlers);
  ctx.currentEvent = "";
}

function dispatchSsePayload(
  raw: string,
  currentEvent: string,
  handlers: SsePostHandlersExtended,
): void {
  if (currentEvent === "delta" || (!currentEvent && raw.startsWith("{"))) {
    try {
      const j = JSON.parse(raw) as { text?: string };
      if (j.text) {
        handlers.onDelta?.(j.text);
      }
    } catch {
      /* ignore malformed chunk */
    }
    return;
  }
  if (currentEvent === "done") {
    try {
      const payload = JSON.parse(raw) as StreamDonePayload;
      handlers.onDone?.(payload);
    } catch {
      handlers.onError?.("Invalid done payload");
    }
    return;
  }
  if (currentEvent === "error") {
    try {
      const j = JSON.parse(raw) as { code?: string; message?: string };
      handlers.onError?.(j.message ?? "error", j.code);
    } catch {
      handlers.onError?.(raw);
    }
  }
}

async function readSseEventStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  decoder: TextDecoder,
  signal: AbortSignal | undefined,
  handlers: SsePostHandlersExtended,
): Promise<void> {
  const ctx: SseLineCtx = { currentEvent: "" };
  let buffer = "";
  while (true) {
    let readResult: ReadableStreamReadResult<Uint8Array>;
    try {
      readResult = await reader.read();
    } catch (e) {
      if (signal?.aborted || isAbortError(e)) {
        handlers.onAbort?.();
        return;
      }
      handlers.onError?.(e instanceof Error ? e.message : String(e), "STREAM");
      return;
    }
    const { done, value } = readResult;
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      handleSseLine(line, ctx, handlers);
    }
  }
}

function isAbortError(e: unknown): boolean {
  if (typeof DOMException !== "undefined" && e instanceof DOMException && e.name === "AbortError") {
    return true;
  }
  return e instanceof Error && e.name === "AbortError";
}
