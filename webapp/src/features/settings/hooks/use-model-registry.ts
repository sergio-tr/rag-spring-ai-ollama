import { apiFetch, apiProductPath } from "@/lib/api-client";
import type {
  ModelRegistryCheckRequest,
  ModelRegistryItemDto,
  ModelRegistryResponseDto,
} from "@/types/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export const modelRegistryQueryKey = ["model-registry"] as const;

export function useModelRegistryQuery() {
  return useQuery({
    queryKey: modelRegistryQueryKey,
    queryFn: () => apiFetch<ModelRegistryResponseDto>(apiProductPath("/model-registry")),
    staleTime: 15_000,
    retry: false,
  });
}

export function useModelRegistryCheckMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ModelRegistryCheckRequest) =>
      apiFetch<ModelRegistryItemDto>(apiProductPath("/model-registry/check"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: modelRegistryQueryKey }),
  });
}
