"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { LlmCatalogResponse } from "@/types/api";

export const llmCatalogQueryKey = ["llm", "catalog"] as const;

export function useLlmCatalog(includeRuntimeStatus = true, provider?: "OPENAI_COMPATIBLE" | "OLLAMA_NATIVE") {
  return useQuery({
    queryKey: [...llmCatalogQueryKey, includeRuntimeStatus, provider ?? "all"] as const,
    queryFn: () => {
      const params = new URLSearchParams();
      params.set("includeRuntimeStatus", includeRuntimeStatus ? "true" : "false");
      if (provider) {
        params.set("provider", provider);
      }
      return apiFetch<LlmCatalogResponse>(apiProductPath(`/llm/catalog?${params.toString()}`));
    },
    staleTime: 30_000,
  });
}
