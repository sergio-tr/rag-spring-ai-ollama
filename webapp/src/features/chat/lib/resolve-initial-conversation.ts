import type { ConversationDto } from "@/types/api";
import { readLastConversationId } from "@/features/chat/lib/last-conversation-persistence";

/**
 * Priority: explicit URL conversation > persisted last chat > most recently updated > none.
 */
export function resolveInitialConversationId(
  conversations: ConversationDto[] | undefined,
  projectId: string | null | undefined,
  urlConversationId: string | null,
): string | null {
  if (urlConversationId) return urlConversationId;
  if (!projectId || !conversations?.length) return null;

  const persisted = readLastConversationId(projectId);
  if (persisted && conversations.some((c) => c.id === persisted)) {
    return persisted;
  }

  const sorted = [...conversations].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  );
  return sorted[0]?.id ?? null;
}
