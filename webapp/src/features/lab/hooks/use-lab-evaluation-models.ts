"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { LabEvaluationModelsResponse, LlmModelCapability } from "@/types/api";

export const labEvaluationModelsQueryKey = ["lab", "evaluation-models"] as const;

export function useLabEvaluationModels(capability: LlmModelCapability) {
  return useQuery({
    queryKey: [...labEvaluationModelsQueryKey, capability] as const,
    queryFn: () =>
      apiFetch<LabEvaluationModelsResponse>(
        apiProductPath(
          `/lab/evaluation-models?capability=${encodeURIComponent(capability)}&includeRuntimeStatus=true`,
        ),
      ),
    staleTime: 30_000,
  });
}
