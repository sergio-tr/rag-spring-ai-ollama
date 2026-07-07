"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MeEffectiveEmbeddingDefaultsResponse } from "@/types/api";

export const meEffectiveEmbeddingDefaultsQueryKey = ["me", "embedding", "effective-defaults"] as const;

export function useMeEffectiveEmbeddingDefaults(projectId?: string | null) {
  const scopedProjectId = projectId ?? undefined;
  return useQuery({
    queryKey: [...meEffectiveEmbeddingDefaultsQueryKey, scopedProjectId ?? "user"] as const,
    queryFn: () => {
      const query = scopedProjectId ? `?projectId=${encodeURIComponent(scopedProjectId)}` : "";
      return apiFetch<MeEffectiveEmbeddingDefaultsResponse>(
        apiProductPath(`/me/embedding/effective-defaults${query}`),
      );
    },
    staleTime: 30_000,
  });
}
