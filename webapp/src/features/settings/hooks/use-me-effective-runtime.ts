import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MeEffectiveRuntimeResponse } from "@/types/api";

export const meEffectiveRuntimeQueryKey = (
  projectId: string | undefined,
  conversationId: string | undefined,
) => ["me", "llm", "effective-runtime", projectId, conversationId] as const;

export function useMeEffectiveRuntime(projectId?: string, conversationId?: string) {
  const enabled = Boolean(projectId && conversationId);
  return useQuery({
    queryKey: meEffectiveRuntimeQueryKey(projectId, conversationId),
    enabled,
    queryFn: () => {
      const params = new URLSearchParams({
        projectId: projectId!,
        conversationId: conversationId!,
      });
      return apiFetch<MeEffectiveRuntimeResponse>(
        apiProductPath(`/me/llm/effective-runtime?${params.toString()}`),
      );
    },
  });
}
