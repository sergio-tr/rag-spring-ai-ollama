"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";

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
    queryKey: ["config", "user"],
    queryFn: () => apiFetch<Record<string, unknown>>(apiProductPath("/config/user")),
  });
}

export function useProjectRagConfigQuery(projectId: string | undefined) {
  return useQuery({
    queryKey: ["config", "project", projectId],
    enabled: Boolean(projectId),
    queryFn: () =>
      apiFetch<Record<string, unknown>>(apiProductPath(`/config/project/${projectId}`)),
  });
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
    },
  });
}
