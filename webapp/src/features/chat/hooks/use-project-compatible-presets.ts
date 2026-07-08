"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import type { ProjectCompatiblePresetsDto } from "@/types/api";

export function projectCompatiblePresetsQueryKey(
  projectId: string,
  embeddingModelId: string | null | undefined,
) {
  return ["projects", projectId, "compatible-presets", embeddingModelId ?? "default"] as const;
}

export function buildProjectCompatiblePresetsPath(
  projectId: string,
  embeddingModelId: string | null | undefined,
): string {
  const params = embeddingModelId
    ? `?embeddingModelId=${encodeURIComponent(embeddingModelId)}`
    : "";
  return apiProductPath(`/projects/${projectId}/compatible-presets${params}`);
}

export function useProjectCompatiblePresets(
  projectId: string | null | undefined,
  options?: { embeddingModelId?: string | null; enabled?: boolean },
) {
  const trimmedProjectId = projectId?.trim() ?? "";
  const profile = useProjectIndexProfile(trimmedProjectId || null);
  const effectiveEmbeddingModelId =
    options?.embeddingModelId?.trim() ||
    profile.data?.embeddingModelId?.trim() ||
    null;
  const missingProjectId = !trimmedProjectId;
  const enabled = !missingProjectId && (options?.enabled ?? true);

  return useQuery({
    queryKey: trimmedProjectId
      ? projectCompatiblePresetsQueryKey(trimmedProjectId, effectiveEmbeddingModelId)
      : ["projects", "compatible-presets", "missing-project"],
    queryFn: () =>
      apiFetch<ProjectCompatiblePresetsDto>(
        buildProjectCompatiblePresetsPath(trimmedProjectId, effectiveEmbeddingModelId),
      ),
    enabled,
    staleTime: 30_000,
    placeholderData: undefined,
  });
}
