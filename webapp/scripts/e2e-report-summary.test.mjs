import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { countListedTests, summarizePlaywrightJson } from "./e2e-report-summary.mjs";

const here = dirname(fileURLToPath(import.meta.url));

describe("e2e-report-summary", () => {
  it("parses Total from --list output", () => {
    assert.equal(countListedTests("Total: 1 test in 1 file\n"), 1);
    assert.equal(Number.isNaN(countListedTests("no tests")), true);
  });

  it("counts failed preflight JSON via stats (Playwright 1.59)", () => {
    const samplePath = resolve(here, "../test-results/preflight-results.json");
    const json = JSON.parse(readFileSync(samplePath, "utf8"));
    const summary = summarizePlaywrightJson(json);
    assert.equal(summary.total, 1);
    assert.equal(summary.failed, 1);
    assert.equal(summary.passed, 0);
  });
});
