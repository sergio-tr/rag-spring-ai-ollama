"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";

export type ProjectIndexProfileDto = {
  projectId: string;
  materializationStrategy: string | null;
  metadataEnabled: boolean;
  metadataProfile: string | null;
  embeddingModelId: string | null;
  chunkMaxChars: number;
  chunkOverlap: number | null;
  profileHash: string;
  createdAt: string;
  updatedAt: string;
};

export type UpsertProjectIndexProfileRequest = {
  materializationStrategy?: string | null;
  metadataEnabled?: boolean | null;
  metadataProfile?: string | null;
  embeddingModelId?: string | null;
  chunkMaxChars?: number | null;
  chunkOverlap?: number | null;
};

export function projectIndexProfileQueryKey(projectId: string) {
  return ["projects", projectId, "index-profile"] as const;
}

export function useProjectIndexProfile(projectId: string | null | undefined) {
  return useQuery({
    queryKey: projectId ? projectIndexProfileQueryKey(projectId) : ["projects", "index-profile", "disabled"],
    enabled: Boolean(projectId),
    queryFn: () => apiFetch<ProjectIndexProfileDto>(apiProductPath(`/projects/${projectId}/index-profile`)),
    staleTime: 30_000,
  });
}

export function useUpsertProjectIndexProfile(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpsertProjectIndexProfileRequest) =>
      apiFetch<ProjectIndexProfileDto>(apiProductPath(`/projects/${projectId}/index-profile`), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: projectIndexProfileQueryKey(projectId) });
    },
  });
}

