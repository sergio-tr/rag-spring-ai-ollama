import { describe, expect, it } from "vitest";
import {
  readGlobalOutcomeCounts,
  readMvpItemOperational,
  readMvpItems,
  readOnExecutedSummary,
  readRollupOutcomeCounts,
} from "@/features/lab/lib/lab-benchmark-mvp-utils";

describe("lab-benchmark-mvp-utils", () => {
  it("readRollupOutcomeCounts handles bad buckets and non-numeric values", () => {
    expect(readRollupOutcomeCounts(null)).toEqual({});
    expect(readRollupOutcomeCounts(undefined)).toEqual({});
    expect(readRollupOutcomeCounts("x")).toEqual({});
    expect(readRollupOutcomeCounts({ outcomeCounts: null })).toEqual({});
    expect(readRollupOutcomeCounts({ outcomeCounts: "bad" })).toEqual({});
    expect(
      readRollupOutcomeCounts({
        outcomeCounts: { A: 1, B: NaN, C: Number.POSITIVE_INFINITY, D: "1", E: 2 },
      }),
    ).toEqual({ A: 1, E: 2 });
  });

  it("readGlobalOutcomeCounts tolerates missing globalMacro", () => {
    expect(readGlobalOutcomeCounts({})).toEqual({});
  });

  it("reads global outcome counts from rollups root", () => {
    const root = {
      globalMacro: {
        outcomeCounts: {
          EXECUTED: 2,
          NOT_SUPPORTED: 1,
        },
      },
    };
    expect(readGlobalOutcomeCounts(root)).toEqual({ EXECUTED: 2, NOT_SUPPORTED: 1 });
  });

  it("reads executed rollout summary when present", () => {
    const bucket = {
      onExecuted: { n: 3, meanNormalizedExactMatch: 0.5 },
    };
    expect(readOnExecutedSummary(bucket)).toEqual({ n: 3, meanNormalizedExactMatch: 0.5 });
  });

  it("readOnExecutedSummary returns null for invalid shapes and non-finite means", () => {
    expect(readOnExecutedSummary(null)).toBeNull();
    expect(readOnExecutedSummary({ onExecuted: null })).toBeNull();
    expect(readOnExecutedSummary({ onExecuted: "x" })).toBeNull();
    expect(readOnExecutedSummary({ onExecuted: { n: "x" } })).toEqual({
      n: 0,
      meanNormalizedExactMatch: null,
    });
    expect(
      readOnExecutedSummary({ onExecuted: { n: 1, meanNormalizedExactMatch: Number.NaN } }),
    ).toEqual({ n: 1, meanNormalizedExactMatch: null });
  });

  it("parses MVP item operational outcome", () => {
    const item = {
      mvp: {
        operational: { outcome: "NOT_SUPPORTED", unsupportedReason: "PRESET_X" },
      },
    };
    expect(readMvpItemOperational(item)).toEqual({
      modelId: null,
      outcome: "NOT_SUPPORTED",
      presetCode: null,
      skipReason: null,
      unsupportedReason: "PRESET_X",
    });
  });

  it("readMvpItemOperational returns null for incomplete rows and trims unsupported reasons", () => {
    expect(readMvpItemOperational(null)).toBeNull();
    expect(readMvpItemOperational({ mvp: {} })).toBeNull();
    expect(readMvpItemOperational({ mvp: { operational: {} } })).toBeNull();
    expect(readMvpItemOperational({ mvp: { operational: { outcome: "" } } })).toBeNull();
    expect(
      readMvpItemOperational({
        mvp: { operational: { outcome: "EXECUTED", unsupportedReason: "  \t  " } },
      }),
    ).toEqual({
      modelId: null,
      outcome: "EXECUTED",
      presetCode: null,
      skipReason: null,
      unsupportedReason: null,
    });
    expect(
      readMvpItemOperational({
        mvp: { operational: { outcome: "OK", unsupportedReason: 42 } },
      }),
    ).toEqual({
      modelId: null,
      outcome: "OK",
      presetCode: null,
      skipReason: null,
      unsupportedReason: null,
    });
  });

  it("readMvpItems returns array or empty", () => {
    expect(readMvpItems({ items: [{ id: 1 }] })).toEqual([{ id: 1 }]);
    expect(readMvpItems({ items: "nope" })).toEqual([]);
    expect(readMvpItems({})).toEqual([]);
  });
});
