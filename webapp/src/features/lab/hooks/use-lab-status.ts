"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { LabStatusResponse } from "@/types/api";

export const labStatusQueryKey = ["lab", "status"] as const;

/**
 * GET {product}/lab/status — feature flags for Lab UI (datasets, classifier proxy, async jobs).
 */
export function useLabStatus() {
  return useQuery({
    queryKey: labStatusQueryKey,
    queryFn: () => apiFetch<LabStatusResponse>(apiProductPath("/lab/status")),
    staleTime: 20_000,
  });
}
