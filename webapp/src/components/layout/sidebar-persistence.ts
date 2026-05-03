/**
 * Sidebar UI persisted in localStorage (projects accordion + shell layout).
 * Keys stay backward-compatible with existing `rag-sidebar` JSON objects.
 */

export const SIDEBAR_STORAGE_KEY = "rag-sidebar";

export type SidebarPersistence = {
  projectsCollapsed: boolean;
  expandedProjectIds: string[];
  /** Whole sidebar rail (icon-only) vs expanded width mode. */
  shellCollapsed?: boolean;
  /** Last expanded-mode width in pixels (rail mode ignores this visually). */
  sidebarWidthPx?: number;
};

const defaultPersistence = (): SidebarPersistence => ({
  projectsCollapsed: false,
  expandedProjectIds: [],
  shellCollapsed: false,
  sidebarWidthPx: undefined,
});

export function readSidebarPersistence(): SidebarPersistence {
  try {
    const raw = localStorage.getItem(SIDEBAR_STORAGE_KEY);
    if (!raw) return defaultPersistence();
    const parsed = JSON.parse(raw) as Partial<SidebarPersistence>;
    return {
      projectsCollapsed: Boolean(parsed.projectsCollapsed),
      expandedProjectIds: Array.isArray(parsed.expandedProjectIds)
        ? parsed.expandedProjectIds.filter((id): id is string => typeof id === "string")
        : [],
      shellCollapsed: Boolean(parsed.shellCollapsed),
      sidebarWidthPx:
        typeof parsed.sidebarWidthPx === "number" && Number.isFinite(parsed.sidebarWidthPx)
          ? parsed.sidebarWidthPx
          : undefined,
    };
  } catch {
    return defaultPersistence();
  }
}

/** Writes a full snapshot (callers merge with {@link readSidebarPersistence} first if needed). */
export function writeSidebarPersistence(next: SidebarPersistence): void {
  try {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, JSON.stringify(next));
  } catch {
    /* quota / private mode */
  }
}

/** Deep-merge-style patch: unspecified fields keep previous values. */
export function patchSidebarPersistence(patch: Partial<SidebarPersistence>): void {
  const prev = readSidebarPersistence();
  writeSidebarPersistence({
    projectsCollapsed: patch.projectsCollapsed ?? prev.projectsCollapsed,
    expandedProjectIds: patch.expandedProjectIds ?? prev.expandedProjectIds,
    shellCollapsed: patch.shellCollapsed ?? prev.shellCollapsed,
    sidebarWidthPx: patch.sidebarWidthPx ?? prev.sidebarWidthPx,
  });
}
