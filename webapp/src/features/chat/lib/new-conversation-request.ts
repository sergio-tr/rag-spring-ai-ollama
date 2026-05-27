/** Matches POST that creates a project-scoped conversation (E2E waitForResponse). */
export function isProjectConversationCreateRequest(method: string, url: string): boolean {
  return method.toUpperCase() === "POST" && /\/projects\/[^/]+\/conversations\/?(\?|$)/i.test(url);
}

export function conversationIdFromChatUrl(url: string): string | null {
  try {
    const id = new URL(url, "http://localhost").searchParams.get("conversationId");
    if (!id) return null;
    return /^[a-f0-9-]{36}$/i.test(id) ? id : null;
  } catch {
    return null;
  }
}
