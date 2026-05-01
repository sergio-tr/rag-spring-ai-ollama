"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useRouter } from "@/navigation";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { useActivateProject } from "@/features/projects/hooks/use-projects";
import { useAppStore } from "@/store/app.store";
import type { ProjectListResponse, ProjectSummary } from "@/types/api";

/** Matches {@link useProjectList}(0, 64) as used by breadcrumb and shell. */
const PROJECT_LIST_QUERY_KEY = ["projects", 0, 64] as const;

export type ProductRouteFallback = "/chat" | "/documents";

/**
 * When `?projectId=` is present on a product route, align persisted active project (cache or GET `/projects/{id}`).
 * On invalid id, replace with `fallbackPath` (query stripped).
 */
export function useSyncActiveProjectFromUrlParam(
  urlProjectId: string | null | undefined,
  fallbackPath: ProductRouteFallback,
) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const mutateAsync = useActivateProject().mutateAsync;
  const activeId = useAppStore((s) => s.activeProject?.id ?? null);

  useEffect(() => {
    const pid = urlProjectId?.trim() || null;
    if (!pid || pid === activeId) {
      return;
    }

    let cancelled = false;

    void (async () => {
      try {
        const cached = queryClient.getQueryData<ProjectListResponse>(PROJECT_LIST_QUERY_KEY);
        let summary: ProjectSummary | null = cached?.items.find((p) => p.id === pid) ?? null;

        if (!summary) {
          try {
            summary = await apiFetch<ProjectSummary>(apiProductPath(`/projects/${pid}`));
          } catch (e) {
            if (e instanceof ApiError && (e.status === 404 || e.status === 403)) {
              summary = null;
            } else {
              throw e;
            }
          }
        }

        if (cancelled) return;
        if (!summary) {
          router.replace(fallbackPath);
          return;
        }

        await mutateAsync({
          id: summary.id,
          name: summary.name,
          iconKey: summary.iconKey,
          colorHex: summary.colorHex,
        });
      } catch {
        if (!cancelled) {
          router.replace(fallbackPath);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [urlProjectId, activeId, queryClient, mutateAsync, router, fallbackPath]);
}
