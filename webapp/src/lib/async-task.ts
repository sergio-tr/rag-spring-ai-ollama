import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { AsyncTaskStatusDto } from "@/types/api";

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Local watchdog expiry while polling — the server job may still be running. */
export class LabJobPollTimeoutError extends Error {
  override readonly name = "LabJobPollTimeoutError";
  readonly lastStatus: AsyncTaskStatusDto | null;

  constructor(lastStatus: AsyncTaskStatusDto | null, message?: string) {
    super(message ?? "Stopped watching — job may still be running on the server.");
    this.lastStatus = lastStatus;
  }
}

async function pollUntilTerminal(
  fetchStatus: () => Promise<AsyncTaskStatusDto>,
  onTick: (status: AsyncTaskStatusDto) => void,
  options?: {
    intervalMs?: number;
    signal?: AbortSignal;
    throwOnFailed?: boolean;
    /** When set, stops polling after this duration (non-terminal jobs surface {@link LabJobPollTimeoutError}). */
    maxWaitMs?: number;
  },
): Promise<AsyncTaskStatusDto> {
  const intervalMs = options?.intervalMs ?? 900;
  const signal = options?.signal;
  const throwOnFailed = options?.throwOnFailed !== false;
  const maxWaitMs = options?.maxWaitMs;
  const startedAt = Date.now();
  for (;;) {
    if (signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    const s = await fetchStatus();
    onTick(s);
    if (s.terminal) {
      if (s.status === "FAILED" && throwOnFailed) {
        throw new Error(s.errorMessage || "Job failed");
      }
      return s;
    }
    if (maxWaitMs != null && Date.now() - startedAt >= maxWaitMs) {
      throw new LabJobPollTimeoutError(s, "Stopped watching — job may still be running on the server.");
    }
    await sleep(intervalMs);
  }
}

/**
 * Polls GET `{product}/me/account/jobs/{id}` (export/deletion jobs — not Lab).
 */
export async function pollAccountJob(
  jobId: string,
  onTick: (status: AsyncTaskStatusDto) => void,
  options?: {
    intervalMs?: number;
    signal?: AbortSignal;
    throwOnFailed?: boolean;
    /** Caps local polling — server job may still run ({@link LabJobPollTimeoutError}). */
    maxWaitMs?: number;
  },
): Promise<AsyncTaskStatusDto> {
  return pollUntilTerminal(
    () => apiFetch<AsyncTaskStatusDto>(apiProductPath(`/me/account/jobs/${jobId}`)),
    onTick,
    options,
  );
}

/**
 * Polls GET {product API prefix}/lab/jobs/{id} with Bearer auth until the task is terminal.
 * (Browser EventSource cannot send Authorization; polling keeps the UI non-blocking vs a single long HTTP call.)
 */
export async function pollLabJob(
  jobId: string,
  onTick: (status: AsyncTaskStatusDto) => void,
  options?: {
    intervalMs?: number;
    signal?: AbortSignal;
    throwOnFailed?: boolean;
    maxWaitMs?: number;
  },
): Promise<AsyncTaskStatusDto> {
  return pollUntilTerminal(
    () => apiFetch<AsyncTaskStatusDto>(apiProductPath(`/lab/jobs/${jobId}`)),
    onTick,
    options,
  );
}

/** One-shot status for Lab job recovery (backend resume hydration). */
export async function fetchLabJobStatusOnce(
  jobId: string,
  options?: { signal?: AbortSignal },
): Promise<AsyncTaskStatusDto> {
  return apiFetch<AsyncTaskStatusDto>(apiProductPath(`/lab/jobs/${jobId}`), { signal: options?.signal });
}
