/** Project-scoped last opened conversation (browser-local). */

const KEY_PREFIX = "chat-last-conversation-v1:";

function storageKey(projectId: string): string {
  return `${KEY_PREFIX}${projectId}`;
}

export function readLastConversationId(projectId: string | null | undefined): string | null {
  if (!projectId || typeof globalThis.localStorage === "undefined") return null;
  try {
    const raw = globalThis.localStorage.getItem(storageKey(projectId))?.trim();
    return raw && /^[a-f0-9-]{36}$/i.test(raw) ? raw : null;
  } catch {
    return null;
  }
}

export function writeLastConversationId(projectId: string | null | undefined, conversationId: string): void {
  if (!projectId || !conversationId || typeof globalThis.localStorage === "undefined") return;
  try {
    globalThis.localStorage.setItem(storageKey(projectId), conversationId);
  } catch {
    // ignore quota / private mode
  }
}

export function clearLastConversationId(projectId: string | null | undefined, conversationId?: string): void {
  if (!projectId || typeof globalThis.localStorage === "undefined") return;
  try {
    const key = storageKey(projectId);
    if (conversationId) {
      const current = globalThis.localStorage.getItem(key);
      if (current !== conversationId) return;
    }
    globalThis.localStorage.removeItem(key);
  } catch {
    // ignore
  }
}
