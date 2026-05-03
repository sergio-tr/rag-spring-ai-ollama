import { getApiBaseUrl, sanitizePlainErrorTextForUi } from "@/lib/api-client";
import { getAccessToken } from "@/lib/access-token";
import { createTraceparent } from "@/lib/traceparent";
import type { AsyncTaskStatusDto } from "@/types/api";

function toAbsoluteUrl(pathOrUrl: string): string {
  if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
    return pathOrUrl;
  }
  const base = getApiBaseUrl();
  const p = pathOrUrl.startsWith("/") ? pathOrUrl : `/${pathOrUrl}`;
  return `${base}${p}`;
}

function parseAsyncTaskStatusLine(raw: string, currentEvent: string): AsyncTaskStatusDto | null {
  const isTaskEvent = currentEvent === "task" || currentEvent === "";
  if (!isTaskEvent && !raw.startsWith("{")) {
    return null;
  }
  return JSON.parse(raw) as AsyncTaskStatusDto;
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

/**
 * Subscribe to GET {product}/lab/jobs/{id}/events (SSE) with Bearer auth.
 * Polling is the default in the UI because it shares the same auth path as `pollLabJob` in `async-task.ts`.
 */
export async function streamLabJob(
  streamPath: string,
  onTick: (s: AsyncTaskStatusDto) => void,
  options?: { signal?: AbortSignal },
): Promise<AsyncTaskStatusDto> {
  const url = toAbsoluteUrl(streamPath);
  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    traceparent: createTraceparent(),
  };
  const bearer = getAccessToken();
  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`;
  }

  const res = await fetch(url, {
    credentials: "include",
    headers,
    signal: options?.signal,
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
        if (line.startsWith("data:")) {
          const raw = line.slice(5).trim();
          if (raw === "" || raw === "[DONE]") {
            continue;
          }
          try {
            const dto = parseAsyncTaskStatusLine(raw, currentEvent);
            if (dto === null) {
              continue;
            }
            onTick(dto);
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

  throw new Error("SSE stream ended before job completed");
}
