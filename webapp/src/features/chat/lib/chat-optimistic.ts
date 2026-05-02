import type { MessageDto } from "@/types/api";

/** True once server messages include the same user text (optimistic bubble can drop). */
export function optimisticConsumed(messages: MessageDto[] | undefined, optimistic: string | null): boolean {
  if (!optimistic?.trim() || !messages?.length) return false;
  const trimmed = optimistic.trim();
  return messages.some((m) => m.role === "USER" && m.content.trim() === trimmed);
}
