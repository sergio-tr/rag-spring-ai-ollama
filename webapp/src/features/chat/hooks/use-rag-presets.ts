"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";
import type { RagPresetDto } from "@/types/api";

/** Shared with settings/presets — list visible RAG presets for the product API. */
export const ragPresetsQueryKey = ["presets"] as const;

export function useRagPresets() {
  return useQuery({
    queryKey: ragPresetsQueryKey,
    queryFn: async () => {
      const raw = await apiFetch<RagPresetDto[]>(apiProductPath("/presets"));
      return raw.map((p) => ({ ...p, name: toProductPresetDisplayName(p.name) }));
    },
    staleTime: 30_000,
  });
}
