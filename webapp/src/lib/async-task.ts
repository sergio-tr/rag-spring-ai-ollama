import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { AsyncTaskStatusDto } from "@/types/api";

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Polls GET `{product}/me/account/jobs/{id}` (export/deletion jobs — not Lab).
 */
export async function pollAccountJob(
  jobId: string,
  onTick: (status: AsyncTaskStatusDto) => void,
  options?: { intervalMs?: number; signal?: AbortSignal; throwOnFailed?: boolean },
): Promise<AsyncTaskStatusDto> {
  const intervalMs = options?.intervalMs ?? 900;
  const signal = options?.signal;
  const throwOnFailed = options?.throwOnFailed !== false;
  for (;;) {
    if (signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    const s = await apiFetch<AsyncTaskStatusDto>(apiProductPath(`/me/account/jobs/${jobId}`));
    onTick(s);
    if (s.terminal) {
      if (s.status === "FAILED" && throwOnFailed) {
        throw new Error(s.errorMessage || "Job failed");
      }
      return s;
    }
    await sleep(intervalMs);
  }
}

/**
 * Polls GET {product API prefix}/lab/jobs/{id} with Bearer auth until the task is terminal.
 * (Browser EventSource cannot send Authorization; polling keeps the UI non-blocking vs a single long HTTP call.)
 */
export async function pollLabJob(
  jobId: string,
  onTick: (status: AsyncTaskStatusDto) => void,
  options?: { intervalMs?: number; signal?: AbortSignal; throwOnFailed?: boolean },
): Promise<AsyncTaskStatusDto> {
  const intervalMs = options?.intervalMs ?? 900;
  const signal = options?.signal;
  const throwOnFailed = options?.throwOnFailed !== false;
  for (;;) {
    if (signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    const s = await apiFetch<AsyncTaskStatusDto>(apiProductPath(`/lab/jobs/${jobId}`));
    onTick(s);
    if (s.terminal) {
      if (s.status === "FAILED" && throwOnFailed) {
        throw new Error(s.errorMessage || "Job failed");
      }
      return s;
    }
    await sleep(intervalMs);
  }
}
