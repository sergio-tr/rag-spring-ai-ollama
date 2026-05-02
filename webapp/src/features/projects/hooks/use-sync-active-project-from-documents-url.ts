"use client";

import { useSyncActiveProjectFromUrlParam } from "@/features/projects/hooks/use-sync-active-project-from-url-param";

/** @see useSyncActiveProjectFromUrlParam */
export function useSyncActiveProjectFromDocumentsUrl(urlProjectId: string | null | undefined) {
  useSyncActiveProjectFromUrlParam(urlProjectId, "/documents");
}
