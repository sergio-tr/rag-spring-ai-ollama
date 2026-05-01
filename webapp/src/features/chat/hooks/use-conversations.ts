"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { useAppStore } from "@/store/app.store";
import type { ConversationDto, MessageDto, PatchConversationBody } from "@/types/api";

const convKey = (projectId: string) => ["conversations", projectId] as const;
const msgKey = (conversationId: string) => ["messages", conversationId] as const;

export function useConversations(projectId: string | undefined) {
  return useQuery({
    queryKey: projectId ? convKey(projectId) : ["conversations", "none"],
    enabled: Boolean(projectId),
    queryFn: () =>
      apiFetch<ConversationDto[]>(apiProductPath(`/projects/${projectId}/conversations`)),
  });
}

export function useCreateConversation(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<ConversationDto>(apiProductPath(`/projects/${projectId}/conversations`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      }),
    onSuccess: () => {
      if (projectId) {
        void qc.invalidateQueries({ queryKey: convKey(projectId) });
        void qc.invalidateQueries({ queryKey: ["projects"] });
      }
    },
  });
}

export function useConversationMessages(conversationId: string | undefined) {
  return useQuery({
    queryKey: conversationId ? msgKey(conversationId) : ["messages", "none"],
    enabled: Boolean(conversationId),
    queryFn: () =>
      apiFetch<MessageDto[]>(apiProductPath(`/conversations/${conversationId}/messages`)),
  });
}

export function usePatchConversation(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, body }: { conversationId: string; body: PatchConversationBody }) =>
      apiFetch<ConversationDto>(apiProductPath(`/conversations/${conversationId}`), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      if (projectId) void qc.invalidateQueries({ queryKey: convKey(projectId) });
    },
  });
}

/** DELETE `/conversations/{conversationId}` → 204 No Content */
export function useDeleteConversation(projectId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (conversationId: string) =>
      apiFetch<void>(apiProductPath(`/conversations/${conversationId}`), {
        method: "DELETE",
      }),
    onSuccess: (_data, conversationId) => {
      void qc.invalidateQueries({ queryKey: msgKey(conversationId) });
      if (projectId) void qc.invalidateQueries({ queryKey: convKey(projectId) });
      void qc.invalidateQueries({ queryKey: ["projects"] });
    },
  });
}

export type MoveConversationVariables = {
  sourceProjectId: string;
  conversationId: string;
  destinationProjectId: string;
  destinationProjectName: string;
};

/** POST `/projects/{sourceProjectId}/conversations/{conversationId}/move`; keeps UX aligned when the active project was the source. */
export function useMoveConversation() {
  const qc = useQueryClient();
  const setActiveProject = useAppStore((s) => s.setActiveProject);

  return useMutation({
    mutationFn: ({
      sourceProjectId,
      conversationId,
      destinationProjectId,
    }: MoveConversationVariables) => {
      const qs = new URLSearchParams({ destinationProjectId });
      return apiFetch<void>(
        apiProductPath(
          `/projects/${sourceProjectId}/conversations/${conversationId}/move?${qs.toString()}`,
        ),
        { method: "POST" },
      );
    },
    onSuccess: (_data, vars) => {
      void qc.invalidateQueries({ queryKey: convKey(vars.sourceProjectId) });
      void qc.invalidateQueries({ queryKey: convKey(vars.destinationProjectId) });
      void qc.invalidateQueries({ queryKey: msgKey(vars.conversationId) });
      void qc.invalidateQueries({ queryKey: ["project-documents", vars.sourceProjectId] });
      void qc.invalidateQueries({ queryKey: ["project-documents", vars.destinationProjectId] });
      void qc.invalidateQueries({ queryKey: ["config", "project", vars.destinationProjectId] });
      void qc.invalidateQueries({ queryKey: ["config", "project", vars.sourceProjectId] });
      void qc.invalidateQueries({ queryKey: ["projects"] });
      if (useAppStore.getState().activeProject?.id === vars.sourceProjectId) {
        setActiveProject({
          id: vars.destinationProjectId,
          name: vars.destinationProjectName,
        });
      }
    },
  });
}
