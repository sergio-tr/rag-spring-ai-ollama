"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ModelsCatalogResponse } from "@/types/api";

const modelsCatalogKey = ["models-catalog"] as const;

export function useModelsCatalog() {
  return useQuery({
    queryKey: modelsCatalogKey,
    queryFn: () => apiFetch<ModelsCatalogResponse>(apiProductPath("/models")),
    staleTime: 30_000,
  });
}
