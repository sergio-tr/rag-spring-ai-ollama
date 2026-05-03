"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { LabStatusResponse } from "@/types/api";

export const labStatusQueryKey = ["lab", "status"] as const;

/** Loads Lab capability flags for the overview (datasets, evaluations, classifier wiring). */
export function useLabStatus() {
  return useQuery({
    queryKey: labStatusQueryKey,
    queryFn: () => apiFetch<LabStatusResponse>(apiProductPath("/lab/status")),
    staleTime: 20_000,
  });
}
