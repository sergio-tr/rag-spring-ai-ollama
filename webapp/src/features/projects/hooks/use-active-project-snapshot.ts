"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";

export type KnowledgeActiveSnapshotResponse = {
  id: string;
  signatureHash: string;
  scopeType: string;
  status: string;
  indexProfileHash: string | null;
  indexProfile: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
} | null;

export function activeProjectSnapshotQueryKey(projectId: string) {
  return ["projects", projectId, "knowledge", "snapshots", "active"] as const;
}

export function useActiveProjectSnapshot(projectId: string | null | undefined) {
  return useQuery({
    queryKey: projectId ? activeProjectSnapshotQueryKey(projectId) : ["projects", "knowledge", "snapshots", "active", "disabled"],
    enabled: Boolean(projectId),
    queryFn: () =>
      apiFetch<KnowledgeActiveSnapshotResponse>(apiProductPath(`/projects/${projectId}/knowledge/snapshots/active`)),
    staleTime: 10_000,
  });
}

