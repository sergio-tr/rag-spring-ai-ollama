"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ProjectDocumentDto } from "@/types/api";

const docsKey = (projectId: string) => ["project-documents", projectId] as const;
const docsKeyForConversation = (projectId: string, conversationId: string) =>
  ["project-documents", projectId, "conversation", conversationId] as const;

const projectsListPrefix = ["projects"] as const;

export function useProjectDocuments(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? docsKey(projectId) : ["project-documents", "none"],
    enabled: Boolean(projectId),
    queryFn: () =>
      apiFetch<ProjectDocumentDto[]>(apiProductPath(`/projects/${projectId}/documents`)),
    refetchInterval: (query) => {
      const rows = query.state.data;
      if (!rows?.some((d) => d.status === "INGESTING")) {
        return false;
      }
      return 2_000;
    },
  });
}

export function useProjectDocumentsForConversation(
  projectId: string | undefined,
  conversationId: string | null | undefined,
) {
  return useQuery({
    queryKey:
      projectId && conversationId
        ? docsKeyForConversation(projectId, conversationId)
        : ["project-documents", "none", "conversation"],
    enabled: Boolean(projectId && conversationId),
    queryFn: () => {
      if (!projectId || !conversationId) return Promise.resolve([]);
      const qs = new URLSearchParams({
        conversationId,
        includeProjectShared: "true",
      });
      return apiFetch<ProjectDocumentDto[]>(
        apiProductPath(`/projects/${projectId}/documents?${qs.toString()}`),
      );
    },
  });
}

export function useUploadProjectDocument(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (file: File) => {
      if (!projectId) throw new Error("no_project");
      const fd = new FormData();
      fd.append("file", file);
      return apiFetch<ProjectDocumentDto>(apiProductPath(`/projects/${projectId}/documents`), {
        method: "POST",
        body: fd,
      });
    },
    onSuccess: () => {
      if (projectId) {
        void qc.invalidateQueries({ queryKey: docsKey(projectId) });
        void qc.invalidateQueries({ queryKey: projectsListPrefix });
      }
    },
  });
}

export function useUploadConversationOverlayDocument(
  projectId: string | undefined,
  conversationId: string | null | undefined,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (file: File) => {
      if (!projectId) throw new Error("no_project");
      if (!conversationId) throw new Error("no_conversation");
      const fd = new FormData();
      fd.append("file", file);
      return apiFetch<ProjectDocumentDto>(
        apiProductPath(`/projects/${projectId}/conversations/${conversationId}/documents`),
        {
          method: "POST",
          body: fd,
        },
      );
    },
    onSuccess: () => {
      if (!projectId || !conversationId) return;
      void qc.invalidateQueries({ queryKey: docsKey(projectId) });
      void qc.invalidateQueries({ queryKey: docsKeyForConversation(projectId, conversationId) });
      void qc.invalidateQueries({ queryKey: projectsListPrefix });
    },
  });
}

export function useDeleteProjectDocument(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (documentId: string) => {
      if (!projectId) throw new Error("no_project");
      await apiFetch(apiProductPath(`/projects/${projectId}/documents/${documentId}`), {
        method: "DELETE",
      });
    },
    onSuccess: () => {
      if (projectId) {
        void qc.invalidateQueries({ queryKey: docsKey(projectId) });
        void qc.invalidateQueries({ queryKey: projectsListPrefix });
      }
    },
  });
}

/**
 * No bulk DELETE API — sequential per-document deletes scoped to `projectId` only.
 */
export function useRetryProjectDocumentIngest(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (documentId: string) => {
      return apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${documentId}/retry-ingest`), {
        method: "POST",
      });
    },
    onSuccess: () => {
      if (projectId) {
        void qc.invalidateQueries({ queryKey: docsKey(projectId) });
      }
    },
  });
}

export function useDeleteAllProjectDocuments(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      if (!projectId) throw new Error("no_project");
      const list = await apiFetch<ProjectDocumentDto[]>(
        apiProductPath(`/projects/${projectId}/documents`),
      );
      for (const doc of list) {
        await apiFetch(apiProductPath(`/projects/${projectId}/documents/${doc.id}`), {
          method: "DELETE",
        });
      }
    },
    onSuccess: () => {
      if (!projectId) return;
      void qc.invalidateQueries({ queryKey: docsKey(projectId) });
      void qc.invalidateQueries({ queryKey: projectsListPrefix });
    },
  });
}
