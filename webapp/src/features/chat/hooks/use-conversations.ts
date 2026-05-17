"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { useAppStore } from "@/store/app.store";
import type { ConversationDto, CreateConversationBody, MessageDto, PatchConversationBody } from "@/types/api";
import { CHAT_DETERMINISTIC_DEFAULT_PRESET_ID } from "@/features/chat/lib/conversation-preset-ui";
import { chatRuntimeStateKey } from "@/features/chat/hooks/use-chat-runtime-state";

const convKey = (projectId: string) => ["conversations", projectId] as const;

/** Applies PATCH fields locally for TanStack optimistic cache updates (order matches server merge semantics). */
export function mergeConversationPatchOptimistic(
  conv: ConversationDto,
  body: PatchConversationBody,
): ConversationDto {
  const next = { ...conv };
  if (body.title !== undefined) next.title = body.title;
  if (body.clearPreset) {
    next.presetId = null;
    next.effectivePresetId = CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
  } else if (body.presetId !== undefined) {
    next.presetId = body.presetId;
    next.effectivePresetId =
      body.presetId != null && String(body.presetId).trim() !== ""
        ? String(body.presetId).trim()
        : CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
  }
  if (body.documentFilter !== undefined) {
    next.documentFilter = [...(body.documentFilter ?? [])];
  }
  if (body.clearRuntimeOverride) {
    next.runtimeOverride = {};
  } else if (body.runtimeOverride !== undefined) {
    next.runtimeOverride = body.runtimeOverride ? { ...body.runtimeOverride } : {};
  }
  if (body.clearLlmModel) {
    next.llmModel = null;
  } else if (body.llmModel !== undefined) {
    next.llmModel = body.llmModel;
  }
  if (body.clearClassifierModelId) {
    next.classifierModelId = null;
  } else if (body.classifierModelId !== undefined) {
    next.classifierModelId = body.classifierModelId;
  }
  if (body.clearPendingClarification) {
    next.pendingClarification = null;
  }
  return next;
}

type PatchConversationContext = {
  previous: ConversationDto[] | undefined;
};
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
  return useMutation<ConversationDto, Error, CreateConversationBody | undefined>({
    mutationFn: (body) =>
      apiFetch<ConversationDto>(apiProductPath(`/projects/${projectId}/conversations`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body ?? {}),
      }),
    onSuccess: (data) => {
      if (projectId) {
        qc.setQueryData<ConversationDto[]>(convKey(projectId), (old) => {
          if (!old) return [data];
          if (old.some((c) => c.id === data.id)) {
            return old.map((c) => (c.id === data.id ? data : c));
          }
          return [data, ...old];
        });
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
    onMutate: async (): Promise<PatchConversationContext> => {
      if (!projectId) return { previous: undefined };
      await qc.cancelQueries({ queryKey: convKey(projectId) });
      return { previous: qc.getQueryData<ConversationDto[]>(convKey(projectId)) };
    },
    onError: (_err, { conversationId }, ctx) => {
      if (projectId && ctx?.previous) {
        qc.setQueryData(convKey(projectId), ctx.previous);
        void qc.invalidateQueries({ queryKey: convKey(projectId) });
      }
      void qc.invalidateQueries({ queryKey: chatRuntimeStateKey(conversationId) });
    },
    onSuccess: (data, { conversationId }) => {
      if (!projectId) return;
      qc.setQueryData<ConversationDto[]>(convKey(projectId), (old) => {
        if (!old) return old;
        return old.map((c) => (c.id === conversationId ? data : c));
      });
      void qc.invalidateQueries({ queryKey: chatRuntimeStateKey(conversationId) });
    },
    onSettled: (_data, _error, vars) => {
      if (projectId) void qc.invalidateQueries({ queryKey: convKey(projectId) });
      if (vars?.conversationId) {
        void qc.invalidateQueries({ queryKey: chatRuntimeStateKey(vars.conversationId) });
      }
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
