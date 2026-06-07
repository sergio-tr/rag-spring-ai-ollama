"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";

export const experimentalPresetCatalogQueryKey = ["lab", "experimental-presets"] as const;

export function useExperimentalPresetCatalog() {
  return useQuery({
    queryKey: experimentalPresetCatalogQueryKey,
    queryFn: () =>
      apiFetch<ExperimentalPresetCatalogItemDto[]>(apiProductPath("/lab/experimental-presets")),
    staleTime: 30_000,
  });
}
