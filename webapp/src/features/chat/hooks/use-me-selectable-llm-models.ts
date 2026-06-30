"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MeSelectableLlmModelsResponse } from "@/types/api";

export const meSelectableLlmModelsQueryKey = ["me", "llm", "selectable-models"] as const;

export function useMeSelectableLlmModels(capability: "CHAT" = "CHAT") {
  return useQuery({
    queryKey: [...meSelectableLlmModelsQueryKey, capability] as const,
    queryFn: () =>
      apiFetch<MeSelectableLlmModelsResponse>(
        apiProductPath(`/me/llm/selectable-models?capability=${encodeURIComponent(capability)}`),
      ),
    staleTime: 30_000,
  });
}
