import type { TraceMetadata, TraceMetadataPrimitive } from "./trace-types";

/** Max keys kept per event to bound memory and serialization size. */
export const TRACE_METADATA_MAX_KEYS = 12;

/** Max string length per metadata value (truncate). */
export const TRACE_METADATA_MAX_STRING_LEN = 512;

/**
 * Drops nested objects/arrays/null; truncates long strings.
 * Call on every write path so the store never retains arbitrary structured data.
 */
export function sanitizeTraceMetadata(meta: TraceMetadata | undefined): TraceMetadata | undefined {
  if (!meta) return undefined;
  const entries = Object.entries(meta).slice(0, TRACE_METADATA_MAX_KEYS);
  const out: Record<string, TraceMetadataPrimitive> = {};
  for (const [key, value] of entries) {
    if (typeof key !== "string" || key.length === 0 || key.length > 64) continue;
    if (typeof value === "number" && Number.isFinite(value)) {
      out[key] = value;
      continue;
    }
    if (typeof value === "boolean") {
      out[key] = value;
      continue;
    }
    if (typeof value === "string") {
      out[key] =
        value.length > TRACE_METADATA_MAX_STRING_LEN
          ? value.slice(0, TRACE_METADATA_MAX_STRING_LEN)
          : value;
    }
  }
  return Object.keys(out).length > 0 ? out : undefined;
}
