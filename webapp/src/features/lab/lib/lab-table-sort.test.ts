import { describe, expect, it } from "vitest";
import {
  compareSortValues,
  sortDirectionIndicator,
  sortRowsByKey,
  toggleTableSort,
} from "@/features/lab/lib/lab-table-sort";

describe("lab-table-sort", () => {
  it("toggleTableSort cycles desc, asc, then clears", () => {
    expect(toggleTableSort(null, "correctness")).toEqual({ key: "correctness", direction: "desc" });
    expect(toggleTableSort({ key: "correctness", direction: "desc" }, "correctness")).toEqual({
      key: "correctness",
      direction: "asc",
    });
    expect(toggleTableSort({ key: "correctness", direction: "asc" }, "correctness")).toBeNull();
    expect(toggleTableSort({ key: "other", direction: "asc" }, "correctness")).toEqual({
      key: "correctness",
      direction: "desc",
    });
  });

  it("sortDirectionIndicator shows arrow only for active column", () => {
    expect(sortDirectionIndicator(null, "x")).toBe("");
    expect(sortDirectionIndicator({ key: "y", direction: "desc" }, "x")).toBe("");
    expect(sortDirectionIndicator({ key: "x", direction: "desc" }, "x")).toBe(" ▼");
    expect(sortDirectionIndicator({ key: "x", direction: "asc" }, "x")).toBe(" ▲");
  });

  it("compareSortValues orders numbers and nulls last", () => {
    expect(compareSortValues(0.2, 0.8)).toBeLessThan(0);
    expect(compareSortValues(null, 0.5)).toBeGreaterThan(0);
    expect(compareSortValues(0.5, null)).toBeLessThan(0);
    expect(compareSortValues(null, null)).toBe(0);
    expect(compareSortValues("-", "a")).toBeGreaterThan(0);
    expect(compareSortValues("NOT_AVAILABLE", "b")).toBeGreaterThan(0);
  });

  it("compareSortValues normalizes booleans, numeric strings, and strings", () => {
    expect(compareSortValues(true, false)).toBeGreaterThan(0);
    expect(compareSortValues("10", "2")).toBeGreaterThan(0);
    expect(compareSortValues("  ", "alpha")).toBeGreaterThan(0);
    expect(compareSortValues("Beta", "alpha")).toBeGreaterThan(0);
    expect(compareSortValues({ toString: () => "Zeta" }, "alpha")).toBeGreaterThan(0);
  });

  it("sortRowsByKey changes row order and toggles direction", () => {
    const rows = [
      { id: "a", score: 0.2 },
      { id: "b", score: 0.9 },
      { id: "c", score: 0.5 },
    ];
    const desc = sortRowsByKey(rows, { key: "score", direction: "desc" }, (row) => row.score);
    expect(desc.map((row) => row.id)).toEqual(["b", "c", "a"]);
    const asc = sortRowsByKey(rows, { key: "score", direction: "asc" }, (row) => row.score);
    expect(asc.map((row) => row.id)).toEqual(["a", "c", "b"]);
    expect(sortRowsByKey(rows, null, (row) => row.score)).toBe(rows);
  });
});
