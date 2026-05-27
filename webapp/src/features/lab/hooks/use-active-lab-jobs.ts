"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ActiveLabJobDto } from "@/types/api";

export const activeLabJobsQueryKey = ["lab", "jobs", "active"] as const;

/** One-shot active job list on mount / session recovery — no periodic polling. */
export function useActiveLabJobs() {
  return useQuery({
    queryKey: activeLabJobsQueryKey,
    queryFn: () => apiFetch<ActiveLabJobDto[]>(apiProductPath("/lab/jobs/active")),
    staleTime: Number.POSITIVE_INFINITY,
    gcTime: Number.POSITIVE_INFINITY,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchInterval: false,
  });
}

