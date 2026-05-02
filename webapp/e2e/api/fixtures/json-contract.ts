import { expect } from "@playwright/test";

/**
 * Guards JSON client regression tests against nginx/HTML error pages or templated stack traces.
 */
export function assertBodyNotHtml(raw: string, context?: string): void {
  const t = raw.trimStart();
  const lower = t.toLowerCase();
  const hint = context ? ` (${context})` : "";
  expect(lower.startsWith("<!doctype html"), `unexpected HTML doctype${hint}`).toBe(false);
  expect(lower.startsWith("<html"), `unexpected root <html>${hint}`).toBe(false);
  expect(lower.includes("<title>403 forbidden</title>"), `unexpected nginx-style forbidden HTML${hint}`).toBe(false);
}

/** Parses JSON after asserting the payload is not an HTML error document. */
export function parseJsonExpectNonHtml(raw: string, context?: string): unknown {
  assertBodyNotHtml(raw, context);
  return JSON.parse(raw);
}
