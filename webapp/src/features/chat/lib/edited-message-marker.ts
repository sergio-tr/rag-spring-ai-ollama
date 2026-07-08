/** Tracks user messages edited in this browser so the timeline can show an "Edited" marker. */

const KEY_PREFIX = "chat-edited-user-messages-v1:";

function storageKey(conversationId: string): string {
  return `${KEY_PREFIX}${conversationId}`;
}

function readSet(conversationId: string): Set<string> {
  if (typeof globalThis.localStorage === "undefined") return new Set();
  try {
    const raw = globalThis.localStorage.getItem(storageKey(conversationId));
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((id): id is string => typeof id === "string" && id.length > 0));
  } catch {
    return new Set();
  }
}

function writeSet(conversationId: string, ids: Set<string>): void {
  if (typeof globalThis.localStorage === "undefined") return;
  try {
    const key = storageKey(conversationId);
    if (ids.size === 0) {
      globalThis.localStorage.removeItem(key);
    } else {
      globalThis.localStorage.setItem(key, JSON.stringify([...ids]));
    }
  } catch {
    // ignore
  }
}

export function readEditedUserMessageIds(conversationId: string): Set<string> {
  return readSet(conversationId);
}

export function isUserMessageMarkedEdited(conversationId: string | null | undefined, messageId: string): boolean {
  if (!conversationId || !messageId) return false;
  return readSet(conversationId).has(messageId);
}

export function markUserMessageEdited(conversationId: string | null | undefined, messageId: string): void {
  if (!conversationId || !messageId) return;
  const next = readSet(conversationId);
  next.add(messageId);
  writeSet(conversationId, next);
}

export function clearEditedMessageMarkers(conversationId: string | null | undefined): void {
  if (!conversationId || typeof globalThis.localStorage === "undefined") return;
  try {
    globalThis.localStorage.removeItem(storageKey(conversationId));
  } catch {
    // ignore
  }
}
