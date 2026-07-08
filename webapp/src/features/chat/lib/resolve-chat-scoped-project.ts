/**
 * Chat routes carry `?projectId=` in the URL; the persisted active project can lag behind
 * while {@link useSyncActiveProjectFromChatUrl} runs. Prefer the URL id for scoped queries.
 */
export function resolveChatScopedProjectId(
  urlProjectId: string | null | undefined,
  activeProjectId: string | undefined,
): { projectId: string | undefined; projectSyncPending: boolean } {
  const normalizedUrlProjectId = urlProjectId?.trim() || null;
  const projectId = normalizedUrlProjectId ?? activeProjectId;
  const projectSyncPending = Boolean(
    normalizedUrlProjectId && normalizedUrlProjectId !== activeProjectId,
  );
  return { projectId, projectSyncPending };
}
