"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { RuntimeConfigCapabilitiesResponse } from "@/types/api";

export const runtimeConfigCapabilitiesQueryKey = ["runtime-config", "capabilities"] as const;

export function useRuntimeConfigCapabilities() {
  return useQuery({
    queryKey: runtimeConfigCapabilitiesQueryKey,
    queryFn: () =>
      apiFetch<RuntimeConfigCapabilitiesResponse>(apiProductPath("/runtime-config/capabilities")),
    staleTime: 30_000,
  });
}

