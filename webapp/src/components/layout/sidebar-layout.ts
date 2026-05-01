/** Default sidebar width when no persisted value exists (matches legacy Tailwind w-[260px]). */
export const DEFAULT_SIDEBAR_WIDTH_PX = 260;

/** Minimum expanded sidebar width (readable labels + controls). */
export const MIN_SIDEBAR_WIDTH_PX = 200;

/** Narrow rail width when the shell sidebar is collapsed to icons only. */
export const RAIL_WIDTH_PX = 56;

/** Fraction of viewport usable as maximum sidebar width (~33%). */
export const SIDEBAR_MAX_VIEWPORT_FRACTION = 0.33;

export function maxSidebarWidthForViewport(viewportWidthPx: number): number {
  return Math.max(MIN_SIDEBAR_WIDTH_PX, Math.floor(viewportWidthPx * SIDEBAR_MAX_VIEWPORT_FRACTION));
}

/**
 * Clamps a desired sidebar width to [min, max] where max scales with viewport (~33%).
 */
export function clampSidebarWidth(widthPx: number, viewportWidthPx: number): number {
  const maxW = maxSidebarWidthForViewport(viewportWidthPx);
  const safe = Number.isFinite(widthPx) ? Math.round(widthPx) : DEFAULT_SIDEBAR_WIDTH_PX;
  return Math.min(maxW, Math.max(MIN_SIDEBAR_WIDTH_PX, safe));
}
