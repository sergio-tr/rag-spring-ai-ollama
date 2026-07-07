"use client";

import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { meEffectiveEmbeddingDefaultsQueryKey } from "@/features/settings/hooks/use-me-effective-embedding-defaults";

export type ConfigSchemaField = {
  key: string;
  type: string;
  min?: number;
  max?: number;
  userEditable: boolean;
};

export type ConfigSchemaResponse = {
  version: number;
  fields: ConfigSchemaField[];
};

const schemaKey = ["config", "schema"] as const;

export function useConfigSchemaQuery() {
  return useQuery({
    queryKey: schemaKey,
    queryFn: () => apiFetch<ConfigSchemaResponse>(apiProductPath("/config/schema")),
    staleTime: 10 * 60 * 1000,
  });
}

export function useUserRagConfigQuery() {
  return useQuery({
    queryKey: ["config", "user", "effective"],
    queryFn: () => apiFetch<Record<string, unknown>>(apiProductPath("/config/user")),
  });
}

export function useUserStoredRagConfigQuery() {
  return useQuery({
    queryKey: ["config", "user", "stored"],
    queryFn: () => apiFetch<Record<string, unknown>>(apiProductPath("/config/user/stored")),
  });
}

export function useProjectRagConfigQuery(projectId: string | undefined) {
  return useQuery({
    queryKey: ["config", "project", projectId, "effective"],
    enabled: Boolean(projectId),
    queryFn: () =>
      apiFetch<Record<string, unknown>>(apiProductPath(`/config/project/${projectId}`)),
  });
}

export function useProjectStoredRagConfigQuery(projectId: string | undefined) {
  return useQuery({
    queryKey: ["config", "project", projectId, "stored"],
    enabled: Boolean(projectId),
    queryFn: () =>
      apiFetch<Record<string, unknown>>(apiProductPath(`/config/project/${projectId}/stored`)),
  });
}

/** Refetch chat/runtime views that derive retrieval defaults from saved config. */
export function invalidateRagConfigDependents(qc: QueryClient) {
  void qc.invalidateQueries({ queryKey: meEffectiveEmbeddingDefaultsQueryKey });
  void qc.invalidateQueries({ queryKey: ["chat-runtime-state"] });
  void qc.invalidateQueries({ queryKey: ["me", "llm", "effective-runtime"] });
}

export function usePutUserRagConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<Record<string, unknown>>(apiProductPath("/config/user"), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["config", "user"] });
      invalidateRagConfigDependents(qc);
    },
  });
}

export function usePutProjectRagConfig(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => {
      if (!projectId) throw new Error("no_project");
      return apiFetch<Record<string, unknown>>(
        apiProductPath(`/config/project/${projectId}`),
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
      );
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["config", "project", projectId] });
      invalidateRagConfigDependents(qc);
    },
  });
}

export function useDeleteProjectRagConfig(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => {
      if (!projectId) throw new Error("no_project");
      return apiFetch(apiProductPath(`/config/project/${projectId}`), { method: "DELETE" });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["config", "project", projectId] });
      invalidateRagConfigDependents(qc);
    },
  });
}
