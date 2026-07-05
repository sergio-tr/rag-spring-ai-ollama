"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ActiveLabJobDto } from "@/types/api";

export const activeLabJobsQueryKey = ["lab", "jobs", "active"] as const;

/** Active job list - refetched on mount, focus, and reconnect for Lab recovery. */
export function useActiveLabJobs() {
  return useQuery({
    queryKey: activeLabJobsQueryKey,
    queryFn: () => apiFetch<ActiveLabJobDto[]>(apiProductPath("/lab/jobs/active")),
    staleTime: 15_000,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchOnMount: "always",
    refetchInterval: false,
  });
}
