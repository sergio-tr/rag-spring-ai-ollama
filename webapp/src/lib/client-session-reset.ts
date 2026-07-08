import type { QueryClient } from "@tanstack/react-query";
import {
  patchSidebarPersistence,
  readSidebarPersistence,
} from "@/components/layout/sidebar-persistence";
import { bumpClientSessionEpoch } from "@/lib/client-session-epoch";
import { resolveAppQueryClient } from "@/lib/query-client-registry";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { useAccountExportSessionStore } from "@/features/settings/store/account-export-session.store";
import { SETTINGS_LAST_PATH_STORAGE_KEY } from "@/features/settings/lib/settings-last-path";
import { useTraceStore } from "@/features/trace/trace.store";
import { useAppStore } from "@/store/app.store";
import { useChatExplainStore } from "@/store/chat-explain.store";

export const LAST_USER_ID_KEY = "rag_last_user_id";

const USER_SCOPED_LOCAL_STORAGE_PREFIXES = [
  "chat-last-conversation-v1:",
  "chat-llm-model-preference-v1:",
  "chat-classifier-model-preference-v1:",
  "chat-edited-user-messages-v1:",
  "lab:evaluation-draft:v1:",
  "rag-lab-form-v1:",
] as const;

const SESSION_STORAGE_KEYS_TO_REMOVE = [
  "rag-lab-jobs",
  "rag-account-export-session-v1",
  SETTINGS_LAST_PATH_STORAGE_KEY,
] as const;

function removeLocalStorageByPrefix(prefix: string): void {
  if (typeof localStorage === "undefined") return;
  const keysToRemove: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith(prefix)) {
      keysToRemove.push(key);
    }
  }
  for (const key of keysToRemove) {
    localStorage.removeItem(key);
  }
}

export type ClientSessionResetOptions = {
  queryClient: QueryClient;
  /** When true, also wipe user-scoped localStorage prefixes (default true). */
  wipePersistedUi?: boolean;
};

/** Clears in-memory and persisted client state that can leak across user sessions. */
export async function resetClientSessionState(
  options: ClientSessionResetOptions,
): Promise<void> {
  const { queryClient, wipePersistedUi = true } = options;

  bumpClientSessionEpoch();
  await queryClient.cancelQueries();
  queryClient.clear();

  useAppStore.getState().setActiveProject(null);
  useAppStore.persist.clearStorage();

  useLabJobSessionStore.getState().resetSession();
  useLabJobSessionStore.persist.clearStorage();

  useAccountExportSessionStore.getState().clearSession();
  useAccountExportSessionStore.persist.clearStorage();

  const chatExplain = useChatExplainStore.getState();
  chatExplain.resetStreaming();
  chatExplain.setLastDone(null);
  chatExplain.setStreaming(false);

  useChatToolbarStore.getState().setApi(null);

  useTraceStore.getState().clearTraceEvents();

  if (!wipePersistedUi) {
    return;
  }

  for (const prefix of USER_SCOPED_LOCAL_STORAGE_PREFIXES) {
    removeLocalStorageByPrefix(prefix);
  }

  const sidebar = readSidebarPersistence();
  patchSidebarPersistence({
    projectsCollapsed: sidebar.projectsCollapsed,
    expandedProjectIds: [],
    shellCollapsed: sidebar.shellCollapsed,
    sidebarWidthPx: sidebar.sidebarWidthPx,
  });

  for (const key of SESSION_STORAGE_KEYS_TO_REMOVE) {
    try {
      sessionStorage.removeItem(key);
    } catch {
      /* quota / private mode */
    }
  }
}

/** Reset using an explicit client or the app-wide registered QueryClient. */
export async function resetRegisteredClientSessionState(
  options: { queryClient?: QueryClient; wipePersistedUi?: boolean } = {},
): Promise<void> {
  const queryClient = resolveAppQueryClient(options.queryClient);
  if (!queryClient) return;
  await resetClientSessionState({
    queryClient,
    wipePersistedUi: options.wipePersistedUi,
  });
}
