"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ActiveLabJobDto } from "@/types/api";

export const activeLabJobsQueryKey = ["lab", "jobs", "active"] as const;

export function useActiveLabJobs() {
  return useQuery({
    queryKey: activeLabJobsQueryKey,
    queryFn: () => apiFetch<ActiveLabJobDto[]>(apiProductPath("/lab/jobs/active")),
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => {
      const jobs = query.state.data;
      return jobs != null && jobs.length > 0 ? 5_000 : false;
    },
  });
}

