/** Helpers for MVP export JSON shapes produced by `GET …/export/mvp/*`. */

export function readRollupOutcomeCounts(rollupBucket: unknown): Record<string, number> {
  if (!rollupBucket || typeof rollupBucket !== "object") {
    return {};
  }
  const oc = (rollupBucket as Record<string, unknown>).outcomeCounts;
  if (!oc || typeof oc !== "object") {
    return {};
  }
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(oc as Record<string, unknown>)) {
    if (typeof v === "number" && Number.isFinite(v)) {
      out[k] = v;
    }
  }
  return out;
}

export function readGlobalOutcomeCounts(rollupsRoot: Record<string, unknown>): Record<string, number> {
  return readRollupOutcomeCounts(rollupsRoot.globalMacro);
}

export function readOnExecutedSummary(rollupBucket: unknown): {
  n: number;
  meanNormalizedExactMatch: number | null;
} | null {
  if (!rollupBucket || typeof rollupBucket !== "object") {
    return null;
  }
  const on = (rollupBucket as Record<string, unknown>).onExecuted;
  if (!on || typeof on !== "object") {
    return null;
  }
  const rec = on as Record<string, unknown>;
  const n = typeof rec.n === "number" ? rec.n : 0;
  const mean =
    typeof rec.meanNormalizedExactMatch === "number" && Number.isFinite(rec.meanNormalizedExactMatch)
      ? rec.meanNormalizedExactMatch
      : null;
  return { n, meanNormalizedExactMatch: mean };
}

export type MvpItemOperational = {
  outcome: string;
  unsupportedReason: string | null;
  skipReason: string | null;
  presetCode: string | null;
  modelId: string | null;
};

export function readMvpItemOperational(item: unknown): MvpItemOperational | null {
  if (!item || typeof item !== "object") {
    return null;
  }
  const mvp = (item as Record<string, unknown>).mvp;
  if (!mvp || typeof mvp !== "object") {
    return null;
  }
  const op = (mvp as Record<string, unknown>).operational;
  if (!op || typeof op !== "object") {
    return null;
  }
  const orec = op as Record<string, unknown>;
  const outcome = typeof orec.outcome === "string" ? orec.outcome : "";
  if (!outcome) {
    return null;
  }
  const ur = orec.unsupportedReason;
  const sr = orec.skipReason;
  const pc = orec.presetCode;
  const mid = orec.modelId;
  return {
    outcome,
    unsupportedReason: typeof ur === "string" && ur.trim() ? ur.trim() : null,
    skipReason: typeof sr === "string" && sr.trim() ? sr.trim() : null,
    presetCode: typeof pc === "string" && pc.trim() ? pc.trim() : null,
    modelId: typeof mid === "string" && mid.trim() ? mid.trim() : null,
  };
}

export function readMvpItems(bundle: Record<string, unknown>): unknown[] {
  const items = bundle.items;
  return Array.isArray(items) ? items : [];
}

export function countOutcomesFromItems(items: unknown[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const item of items) {
    if (!item || typeof item !== "object") continue;
    const rec = item as Record<string, unknown>;
    const op = readMvpItemOperational(item);
    const outcome =
      op?.outcome ??
      (typeof rec.status === "string" && rec.status.trim() ? rec.status.trim() : "");
    if (!outcome) continue;
    counts[outcome] = (counts[outcome] ?? 0) + 1;
  }
  return counts;
}
