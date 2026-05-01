/**
 * Phase 4A — “Open project” navigation contract.
 *
 * Product order when choosing the post-open destination:
 * 1. Project dashboard/detail — not implemented as a dedicated route; skipped.
 * 2. Project-scoped Chat — primary workspace; `/chat` with `projectId` (+ optional `conversationId`).
 * 3. Documents — fallback only if Chat were unavailable (Chat route exists; not used as default).
 *
 * Always include `projectId` in chat URLs so reload/share can reconcile active project via
 * {@link useSyncActiveProjectFromChatUrl} without relying on persisted store alone.
 */
export function buildProjectScopedChatHref(
  projectId: string,
  conversationId?: string | null,
): string {
  const params = new URLSearchParams();
  params.set("projectId", projectId);
  if (conversationId) {
    params.set("conversationId", conversationId);
  }
  return `/chat?${params.toString()}`;
}

/** Documents list scoped to a project (Phase 4B). Sync active project via {@link useSyncActiveProjectFromDocumentsUrl}. */
export function buildProjectScopedDocumentsHref(projectId: string): string {
  const params = new URLSearchParams();
  params.set("projectId", projectId);
  return `/documents?${params.toString()}`;
}
