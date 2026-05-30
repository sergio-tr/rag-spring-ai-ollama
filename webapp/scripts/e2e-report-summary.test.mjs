import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { countListedTests, shouldFailOnExplicitSkips, summarizePlaywrightJson } from "./e2e-report-summary.mjs";

/** Playwright 1.59+ JSON reporter shape (stats root); no on-disk test-results artifact required. */
const FAILED_PREFLIGHT_JSON = {
  stats: {
    expected: 0,
    unexpected: 1,
    skipped: 0,
    flaky: 0,
  },
  suites: [],
};

describe("e2e-report-summary", () => {
  it("parses Total from --list output", () => {
    assert.equal(countListedTests("Total: 1 test in 1 file\n"), 1);
    assert.equal(Number.isNaN(countListedTests("no tests")), true);
  });

  it("counts failed preflight JSON via stats (Playwright 1.59+)", () => {
    const summary = summarizePlaywrightJson(FAILED_PREFLIGHT_JSON);
    assert.equal(summary.total, 1);
    assert.equal(summary.failed, 1);
    assert.equal(summary.passed, 0);
  });

  it("does not treat maxFailures-aborted skips as explicit skip guard failures", () => {
    const summary = summarizePlaywrightJson({
      stats: { expected: 0, unexpected: 1, skipped: 10, flaky: 0 },
      suites: [],
    });
    assert.equal(summary.failed, 1);
    assert.equal(summary.skipped, 10);
    assert.equal(shouldFailOnExplicitSkips(summary, { E2E_FAIL_ON_SKIPS: "1" }), false);
  });

  it("fails explicit skip guard when tests skipped without failures", () => {
    const summary = { total: 3, passed: 1, failed: 0, skipped: 2 };
    assert.equal(shouldFailOnExplicitSkips(summary, { E2E_FAIL_ON_SKIPS: "1" }), true);
  });
});
