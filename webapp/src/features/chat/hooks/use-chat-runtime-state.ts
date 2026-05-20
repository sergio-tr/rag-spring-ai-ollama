"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ChatRuntimeStateDto } from "@/types/api";

export const chatRuntimeStateKey = (conversationId: string) =>
  ["chat-runtime-state", conversationId] as const;

export function useChatRuntimeState(conversationId: string | null | undefined) {
  return useQuery({
    queryKey: conversationId ? chatRuntimeStateKey(conversationId) : ["chat-runtime-state", "none"],
    enabled: Boolean(conversationId),
    queryFn: () =>
      apiFetch<ChatRuntimeStateDto>(apiProductPath(`/conversations/${conversationId}/runtime-state`)),
  });
}

