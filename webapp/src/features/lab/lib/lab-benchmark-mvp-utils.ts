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
  const results = bundle.results;
  if (Array.isArray(results)) {
    return results.map(normalizeV1ResultRow);
  }
  const items = bundle.items;
  return Array.isArray(items) ? items : [];
}

export function readRollupsFromResultsBundle(bundle: Record<string, unknown>): Record<string, unknown> {
  const metrics = bundle.metrics;
  return metrics && typeof metrics === "object" ? (metrics as Record<string, unknown>) : {};
}

function normalizeV1ResultRow(row: unknown): unknown {
  if (!row || typeof row !== "object") {
    return row;
  }
  const rec = row as Record<string, unknown>;
  const answer = rec.answer && typeof rec.answer === "object" ? (rec.answer as Record<string, unknown>) : null;
  const technical =
    rec.technical && typeof rec.technical === "object" ? (rec.technical as Record<string, unknown>) : null;
  return {
    ...rec,
    questionText: rec.questionText ?? rec.question,
    actualAnswer: rec.actualAnswer ?? (typeof answer?.text === "string" ? answer.text : ""),
    mvp: rec.mvp ?? rec.metrics,
    metricsPayload: rec.metricsPayload ?? technical?.metricsPayload,
  };
}

export function readComparisonScore(row: unknown, key: string): number | null {
  if (!row || typeof row !== "object") {
    return null;
  }
  const value = (row as Record<string, unknown>)[key];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function readAnswerableScoreFromComparisonRow(row: unknown): number | null {
  return readComparisonScore(row, "scoreAnswerable");
}

/** Stable error bucket from v1 export rows; null for executed or missing items. */
export function readDerivedErrorClassFromItem(item: unknown): string | null {
  if (!item || typeof item !== "object") {
    return null;
  }
  const rec = item as Record<string, unknown>;
  const top = typeof rec.derivedErrorClass === "string" ? rec.derivedErrorClass.trim() : "";
  if (top) {
    return top;
  }
  const mvp = rec.mvp && typeof rec.mvp === "object" ? (rec.mvp as Record<string, unknown>) : null;
  const op = mvp?.operational && typeof mvp.operational === "object" ? (mvp.operational as Record<string, unknown>) : null;
  const fromOp = typeof op?.derivedErrorClass === "string" ? op.derivedErrorClass.trim() : "";
  return fromOp || null;
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
