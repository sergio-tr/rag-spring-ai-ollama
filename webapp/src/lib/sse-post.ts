import { getApiBaseUrl } from "@/lib/api-client";
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
 * Lifecycle: pass an {@link AbortSignal} to cancel the fetch and the body reader. There is **no** automatic
 * retry (avoids duplicate assistant messages). Use {@link createTraceparent} via the `traceparent` header for OTEL correlation.
 */
export async function postSseJson(
  path: string,
  body: unknown,
  signal: AbortSignal | undefined,
  handlers: SsePostHandlersExtended,
): Promise<void> {
  const base = getApiBaseUrl();
  const url = path.startsWith("http") ? path : `${base}${path.startsWith("/") ? path : `/${path}`}`;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
    traceparent: createTraceparent(),
  };
  const bearer = getAccessToken();
  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`;
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
        headers,
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
    let buffer = "";
    let currentEvent = "";

    try {
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
          if (line.startsWith("data:")) {
            const raw = line.slice(5).trim();
            if (raw === "[DONE]") {
              currentEvent = "";
              continue;
            }
            if (currentEvent === "delta" || (!currentEvent && raw.startsWith("{"))) {
              try {
                const j = JSON.parse(raw) as { text?: string };
                if (j.text) handlers.onDelta?.(j.text);
              } catch {
                /* ignore malformed chunk */
              }
            } else if (currentEvent === "done") {
              try {
                const payload = JSON.parse(raw) as StreamDonePayload;
                handlers.onDone?.(payload);
              } catch {
                handlers.onError?.("Invalid done payload");
              }
            } else if (currentEvent === "error") {
              try {
                const j = JSON.parse(raw) as { code?: string; message?: string };
                handlers.onError?.(j.message ?? "error", j.code);
              } catch {
                handlers.onError?.(raw);
              }
            }
            currentEvent = "";
          }
        }
      }
    } finally {
      if (reader) {
        await reader.cancel().catch(() => {});
      }
    }
  } finally {
    if (signal) {
      signal.removeEventListener("abort", onAbort);
    }
  }
}

function isAbortError(e: unknown): boolean {
  if (typeof DOMException !== "undefined" && e instanceof DOMException && e.name === "AbortError") {
    return true;
  }
  return e instanceof Error && e.name === "AbortError";
}
