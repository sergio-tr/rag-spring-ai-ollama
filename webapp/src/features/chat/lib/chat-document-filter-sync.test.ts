import { describe, expect, it } from "vitest";
import { documentFiltersEqual } from "./chat-document-filter-sync";

describe("documentFiltersEqual", () => {
  it("returns true for same ids different order", () => {
    expect(documentFiltersEqual(["b", "a"], ["a", "b"])).toBe(true);
  });

  it("returns false when lengths differ", () => {
    expect(documentFiltersEqual(["a"], ["a", "b"])).toBe(false);
  });

  it("treats undefined and empty as equal", () => {
    expect(documentFiltersEqual(undefined, [])).toBe(true);
  });
});
