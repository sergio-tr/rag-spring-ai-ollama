"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MeEffectiveLlmDefaultsResponse } from "@/types/api";

export const meEffectiveLlmDefaultsQueryKey = ["me", "llm", "effective-defaults"] as const;

export function useMeEffectiveLlmDefaults() {
  return useQuery({
    queryKey: meEffectiveLlmDefaultsQueryKey,
    queryFn: () => apiFetch<MeEffectiveLlmDefaultsResponse>(apiProductPath("/me/llm/effective-defaults")),
    staleTime: 30_000,
  });
}

