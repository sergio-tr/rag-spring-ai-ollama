"use client";

import { useEffect } from "react";
import { useAppStore } from "@/store/app.store";
import type { ProjectSummary } from "@/types/api";

/**
 * Clears persisted active project when the project list is loaded and the id is missing
 * (deleted elsewhere or stale storage).
 */
export function useSyncActiveProjectWithList(items: ProjectSummary[] | undefined) {
  const active = useAppStore((s) => s.activeProject);
  const setActive = useAppStore((s) => s.setActiveProject);

  useEffect(() => {
    if (items === undefined) return;
    if (!active) return;
    if (items.length === 0) {
      setActive(null);
      return;
    }
    if (!items.some((p) => p.id === active.id)) {
      setActive(null);
    }
  }, [items, active, setActive]);
}
