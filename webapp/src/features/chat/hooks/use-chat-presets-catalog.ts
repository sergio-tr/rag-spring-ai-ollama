"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";

export type ChatPresetCatalogDto = {
  productPresets: RagPresetDto[];
  experimentalPresets: ExperimentalPresetCatalogItemDto[];
};

export const chatPresetCatalogQueryKey = ["chat", "presets", "catalog"] as const;

export function useChatPresetsCatalog() {
  return useQuery({
    queryKey: chatPresetCatalogQueryKey,
    queryFn: () => apiFetch<ChatPresetCatalogDto>(apiProductPath("/chat/presets/catalog")),
    staleTime: 30_000,
  });
}

