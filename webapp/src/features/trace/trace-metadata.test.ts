import { describe, it, expect } from "vitest";
import { sanitizeTraceMetadata, TRACE_METADATA_MAX_KEYS, TRACE_METADATA_MAX_STRING_LEN } from "./trace-metadata";

describe("sanitizeTraceMetadata", () => {
  it("returns undefined for empty input", () => {
    expect(sanitizeTraceMetadata(undefined)).toBeUndefined();
    expect(sanitizeTraceMetadata({})).toBeUndefined();
  });

  it("keeps finite numbers and booleans", () => {
    expect(sanitizeTraceMetadata({ n: 42, ok: true })).toEqual({ n: 42, ok: true });
  });

  it("truncates long strings", () => {
    const long = "a".repeat(TRACE_METADATA_MAX_STRING_LEN + 50);
    const out = sanitizeTraceMetadata({ k: long });
    expect(out?.k).toHaveLength(TRACE_METADATA_MAX_STRING_LEN);
  });

  it("drops keys beyond TRACE_METADATA_MAX_KEYS", () => {
    const meta: Record<string, string> = {};
    for (let i = 0; i < TRACE_METADATA_MAX_KEYS + 5; i += 1) {
      meta[`k${i}`] = String(i);
    }
    const out = sanitizeTraceMetadata(meta);
    expect(Object.keys(out ?? {}).length).toBe(TRACE_METADATA_MAX_KEYS);
  });

  it("ignores invalid values", () => {
    expect(sanitizeTraceMetadata({ bad: null as unknown as string })).toBeUndefined();
    expect(sanitizeTraceMetadata({ nested: { x: 1 } as unknown as string })).toBeUndefined();
  });
});
