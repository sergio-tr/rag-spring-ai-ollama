"use client";

import { useQuery } from "@tanstack/react-query";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import type { BenchmarkKind, LatestLabRunRecoveryDto } from "@/types/api";
import { latestLabBenchmarkRunQueryKey } from "@/features/lab/lib/lab-run-recovery";

export { latestLabBenchmarkRunQueryKey };

/** Latest benchmark run for Lab page recovery when no active job is listed. */
export function useLatestLabBenchmarkRun(options: Readonly<{
  benchmarkKind: BenchmarkKind;
  projectId: string | null;
  enabled?: boolean;
}>) {
  const { benchmarkKind, projectId, enabled = true } = options;
  const path =
    projectId?.trim()
      ? `${apiProductPath(`/lab/benchmarks/${benchmarkKind}/runs/latest`)}?projectId=${encodeURIComponent(projectId.trim())}`
      : apiProductPath(`/lab/benchmarks/${benchmarkKind}/runs/latest`);

  return useQuery({
    queryKey: latestLabBenchmarkRunQueryKey(benchmarkKind, projectId),
    queryFn: () => apiFetch<LatestLabRunRecoveryDto>(path),
    enabled,
    staleTime: 30_000,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) {
        return false;
      }
      return failureCount < 2;
    },
  });
}
