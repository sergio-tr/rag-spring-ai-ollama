"use client";

import { useEffect, useRef } from "react";

type UseJobPollingOptions<T> = {
  /** Polling interval in ms */
  intervalMs?: number;
  enabled?: boolean;
  fetcher: () => Promise<T>;
  onUpdate?: (data: T) => void;
  /** Stop polling when true */
  stopWhen?: (data: T) => boolean;
};

/**
 * Generic polling helper for async jobs (e.g. document ingestion status).
 */
export function useJobPolling<T>({
  intervalMs = 2000,
  enabled = true,
  fetcher,
  onUpdate,
  stopWhen,
}: UseJobPollingOptions<T>) {
  const tick = useRef(0);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const id = window.setInterval(async () => {
      tick.current += 1;
      try {
        const data = await fetcher();
        if (cancelled) return;
        onUpdate?.(data);
        if (stopWhen?.(data)) {
          window.clearInterval(id);
        }
      } catch {
        /* caller may surface via fetcher */
      }
    }, intervalMs);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [enabled, intervalMs, fetcher, onUpdate, stopWhen]);
}
