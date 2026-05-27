import type { MessageDto } from "@/types/api";

/** Stable logical sequence for ordering (never use updatedAt — messages have no updatedAt). */
export function messageSeq(message: MessageDto, fallbackIndex = 0): number {
  if (typeof message.seq === "number" && Number.isFinite(message.seq)) {
    return message.seq;
  }
  return fallbackIndex;
}

/** Sort conversation messages by server sequence, then createdAt, then id. */
export function sortMessagesBySeq(messages: MessageDto[]): MessageDto[] {
  return messages
    .map((m, index) => ({ m, index }))
    .sort((a, b) => {
      const as = messageSeq(a.m, a.index);
      const bs = messageSeq(b.m, b.index);
      if (as !== bs) return as - bs;
      const ac = Date.parse(a.m.createdAt);
      const bc = Date.parse(b.m.createdAt);
      if (Number.isFinite(ac) && Number.isFinite(bc) && ac !== bc) return ac - bc;
      return a.m.id.localeCompare(b.m.id);
    })
    .map(({ m }) => m);
}

/**
 * Optimistic cache after PATCH edit: update user content in place and drop tail (seq > edited).
 * Matches backend soft-delete of messages with higher seq.
 */
export function applyUserMessageEditOptimistic(
  messages: MessageDto[],
  userMessageId: string,
  newContent: string,
): MessageDto[] {
  const edited = messages.find((m) => m.id === userMessageId && m.role === "USER");
  if (!edited) {
    return messages;
  }
  const editedSeq = messageSeq(
    edited,
    messages.findIndex((m) => m.id === userMessageId),
  );
  return messages
    .filter((m, index) => {
      const s = messageSeq(m, index);
      return s <= editedSeq;
    })
    .map((m) => (m.id === userMessageId ? { ...m, content: newContent } : m));
}
