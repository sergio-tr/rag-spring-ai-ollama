"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MeEffectiveEmbeddingDefaultsResponse } from "@/types/api";

export const meEffectiveEmbeddingDefaultsQueryKey = ["me", "embedding", "effective-defaults"] as const;

export function useMeEffectiveEmbeddingDefaults() {
  return useQuery({
    queryKey: meEffectiveEmbeddingDefaultsQueryKey,
    queryFn: () =>
      apiFetch<MeEffectiveEmbeddingDefaultsResponse>(apiProductPath("/me/embedding/effective-defaults")),
    staleTime: 30_000,
  });
}
