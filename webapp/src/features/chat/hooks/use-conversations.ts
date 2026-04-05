"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
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
      if (projectId) void qc.invalidateQueries({ queryKey: convKey(projectId) });
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
