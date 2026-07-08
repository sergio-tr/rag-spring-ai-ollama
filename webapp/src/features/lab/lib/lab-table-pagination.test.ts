import { describe, expect, it } from "vitest";
import { paginateRows } from "./lab-table-pagination";

describe("paginateRows", () => {
  const rows = Array.from({ length: 100 }, (_, index) => `row-${index + 1}`);

  it("returns the requested page slice", () => {
    const page1 = paginateRows(rows, 1, 25);
    expect(page1.pageRows).toHaveLength(25);
    expect(page1.pageRows[0]).toBe("row-1");
    expect(page1.rangeStart).toBe(1);
    expect(page1.rangeEnd).toBe(25);
    expect(page1.totalPages).toBe(4);

    const page4 = paginateRows(rows, 4, 25);
    expect(page4.pageRows).toHaveLength(25);
    expect(page4.pageRows[24]).toBe("row-100");
    expect(page4.rangeStart).toBe(76);
    expect(page4.rangeEnd).toBe(100);
  });

  it("clamps page when out of range", () => {
    const slice = paginateRows(rows, 99, 50);
    expect(slice.page).toBe(2);
    expect(slice.pageRows[0]).toBe("row-51");
  });
});
