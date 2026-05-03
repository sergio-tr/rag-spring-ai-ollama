import { describe, it, expect } from "vitest";
import { dedupeRepeatedLocaleSegments } from "./dedupe-locale-path";

describe("dedupeRepeatedLocaleSegments", () => {
  it("leaves single-locale paths unchanged", () => {
    expect(dedupeRepeatedLocaleSegments("/en/login")).toBe("/en/login");
    expect(dedupeRepeatedLocaleSegments("/es/settings")).toBe("/es/settings");
  });

  it("collapses /en/en/login to /en/login", () => {
    expect(dedupeRepeatedLocaleSegments("/en/en/login")).toBe("/en/login");
  });

  it("collapses /es/es/login to /es/login", () => {
    expect(dedupeRepeatedLocaleSegments("/es/es/login")).toBe("/es/login");
  });

  it("collapses multiple repeated locale pairs", () => {
    expect(dedupeRepeatedLocaleSegments("/en/en/en/login")).toBe("/en/login");
  });

  it("does not strip unequal consecutive locales", () => {
    expect(dedupeRepeatedLocaleSegments("/en/es/login")).toBe("/en/es/login");
  });

  it("does not prefix API routes", () => {
    expect(dedupeRepeatedLocaleSegments("/api/v5/auth/session")).toBe("/api/v5/auth/session");
  });

  it("handles locale-only duplicate segments", () => {
    expect(dedupeRepeatedLocaleSegments("/en/en")).toBe("/en");
  });
});
