"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { RagPresetDto } from "@/types/api";

/** Shared with settings/presets — list visible RAG presets for the product API. */
export const ragPresetsQueryKey = ["presets"] as const;

export function useRagPresets() {
  return useQuery({
    queryKey: ragPresetsQueryKey,
    queryFn: () => apiFetch<RagPresetDto[]>(apiProductPath("/presets")),
    staleTime: 30_000,
  });
}
