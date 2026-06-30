"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { LlmCatalogResponse } from "@/types/api";

export const llmCatalogQueryKey = ["llm", "catalog"] as const;

export function useLlmCatalog(includeRuntimeStatus = true) {
  return useQuery({
    queryKey: [...llmCatalogQueryKey, includeRuntimeStatus] as const,
    queryFn: () =>
      apiFetch<LlmCatalogResponse>(
        apiProductPath(
          `/llm/catalog?includeRuntimeStatus=${includeRuntimeStatus ? "true" : "false"}`,
        ),
      ),
    staleTime: 30_000,
  });
}
