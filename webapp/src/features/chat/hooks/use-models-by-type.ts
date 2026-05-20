"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { SelectableModelDto } from "@/types/api";

type ModelType = "LLM" | "EMBEDDING";

export function useModelsByType(type: ModelType) {
  return useQuery({
    queryKey: ["models-by-type", type] as const,
    queryFn: () => apiFetch<SelectableModelDto[]>(apiProductPath(`/models?type=${encodeURIComponent(type)}`)),
    staleTime: 30_000,
  });
}

