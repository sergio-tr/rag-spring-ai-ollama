import type { QueryClient } from "@tanstack/react-query";

import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ConversationDto } from "@/types/api";

/** Must match `use-conversations` query key prefix for cache coherence. */
export function conversationsQueryKey(projectId: string) {
  return ["conversations", projectId] as const;
}

/**
 * Returns the most recently updated conversation id, or null if the project has none.
 */
export async function fetchLatestConversationId(
  queryClient: QueryClient,
  projectId: string,
): Promise<string | null> {
  const list = await queryClient.fetchQuery({
    queryKey: conversationsQueryKey(projectId),
    queryFn: () =>
      apiFetch<ConversationDto[]>(apiProductPath(`/projects/${projectId}/conversations`)),
  });
  const sorted = [...list].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  );
  if (sorted[0]?.id) {
    return sorted[0].id;
  }
  return null;
}
