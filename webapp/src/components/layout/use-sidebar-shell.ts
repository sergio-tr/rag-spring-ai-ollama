"use client";

import { useCallback, useEffect, useState } from "react";
import {
  clampSidebarWidth,
  DEFAULT_SIDEBAR_WIDTH_PX,
  RAIL_WIDTH_PX,
} from "@/components/layout/sidebar-layout";
import { patchSidebarPersistence, readSidebarPersistence } from "@/components/layout/sidebar-persistence";

type SidebarShellState = Readonly<{
  railCollapsed: boolean;
  expandedWidthPx: number;
  viewportWidthPx: number;
  toggleRailCollapsed: () => void;
  applyResizeDelta: (deltaXPx: number) => void;
}>;

/**
 * Desktop sidebar shell: persisted rail mode + expanded width, clamped to viewport.
 */
export function useSidebarShell(): SidebarShellState {
  const [railCollapsed, setRailCollapsed] = useState(false);
  const [expandedWidthPx, setExpandedWidthPx] = useState(DEFAULT_SIDEBAR_WIDTH_PX);
  const [viewportWidthPx, setViewportWidthPx] = useState(1280);

  useEffect(() => {
    const onResize = () => {
      const vw = window.innerWidth;
      setViewportWidthPx(vw);
      setExpandedWidthPx((w) => clampSidebarWidth(w, vw));
    };

    // Hydrate shell layout from localStorage after mount (avoid SSR/localStorage mismatch).
    const p = readSidebarPersistence();
    const vw = window.innerWidth;
    queueMicrotask(() => {
      setRailCollapsed(Boolean(p.shellCollapsed));
      setViewportWidthPx(vw);
      setExpandedWidthPx(clampSidebarWidth(p.sidebarWidthPx ?? DEFAULT_SIDEBAR_WIDTH_PX, vw));
    });

    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const toggleRailCollapsed = useCallback(() => {
    setRailCollapsed((prev) => {
      const next = !prev;
      patchSidebarPersistence({ shellCollapsed: next });
      return next;
    });
  }, []);

  const applyResizeDelta = useCallback(
    (deltaXPx: number) => {
      if (railCollapsed) return;
      setExpandedWidthPx((prev) => {
        const next = clampSidebarWidth(prev + deltaXPx, viewportWidthPx);
        patchSidebarPersistence({ sidebarWidthPx: next });
        return next;
      });
    },
    [railCollapsed, viewportWidthPx],
  );

  return {
    railCollapsed,
    expandedWidthPx,
    viewportWidthPx,
    toggleRailCollapsed,
    applyResizeDelta,
  };
}

export { RAIL_WIDTH_PX };
