"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ActivateClassifierModelBody, ClassifierModelRegistryEntryDto } from "@/types/api";

export const classifierModelsQueryKey = ["lab", "classifier-models"] as const;

export function useClassifierModelsQuery(enabled: boolean) {
  return useQuery({
    queryKey: classifierModelsQueryKey,
    queryFn: () => apiFetch<ClassifierModelRegistryEntryDto[]>(apiProductPath("/lab/classifier/models")),
    enabled,
    staleTime: 15_000,
  });
}

export function useActivateClassifierModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ modelId, body }: { modelId: string; body: ActivateClassifierModelBody }) =>
      apiFetch<ClassifierModelRegistryEntryDto>(
        apiProductPath(`/lab/classifier/models/${modelId}/activate`),
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
      ),
    onSuccess: (_, variables) => {
      void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
      void qc.invalidateQueries({ queryKey: ["config", "project", variables.body.projectId] });
    },
  });
}
