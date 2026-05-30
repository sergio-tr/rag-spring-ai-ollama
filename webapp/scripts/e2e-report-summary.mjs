/**
 * Parse Playwright JSON reporter output for CI guards.
 * Supports Playwright 1.59+ nested suites/specs and root `stats`.
 */

export function countListedTests(output) {
  const match = output.match(/Total:\s+(\d+)\s+tests?/i);
  return match ? Number.parseInt(match[1], 10) : Number.NaN;
}

export function collectLeafTests(node, acc = []) {
  if (node == null || typeof node !== "object") {
    return acc;
  }
  if (Array.isArray(node)) {
    for (const item of node) collectLeafTests(item, acc);
    return acc;
  }
  if (Array.isArray(node.tests)) {
    for (const test of node.tests) {
      if (test && typeof test === "object") acc.push(test);
    }
  }
  if (Array.isArray(node.specs)) {
    for (const spec of node.specs) {
      collectLeafTests(spec, acc);
    }
  }
  if (Array.isArray(node.suites)) {
    for (const suite of node.suites) {
      collectLeafTests(suite, acc);
    }
  }
  return acc;
}

export function summarizePlaywrightJson(json) {
  const stats = json?.stats;
  if (stats && typeof stats === "object") {
    const expected = Number(stats.expected ?? 0);
    const unexpected = Number(stats.unexpected ?? 0);
    const skipped = Number(stats.skipped ?? 0);
    const flaky = Number(stats.flaky ?? 0);
    const total = expected + unexpected + skipped + flaky;
    if (total > 0) {
      return { total, passed: expected + flaky, failed: unexpected, skipped };
    }
  }

  const leaves = collectLeafTests(json);
  let skipped = 0;
  let passed = 0;
  let failed = 0;
  for (const test of leaves) {
    const results = Array.isArray(test.results) ? test.results : [];
    const statuses = results.map((r) => r?.status).filter(Boolean);
    const status = typeof test.status === "string" ? test.status : "";
    if (status === "skipped" || (statuses.length > 0 && statuses.every((s) => s === "skipped"))) {
      skipped += 1;
    } else if (
      status === "unexpected" ||
      statuses.some((s) => ["failed", "timedOut", "interrupted"].includes(String(s)))
    ) {
      failed += 1;
    } else {
      passed += 1;
    }
  }
  return { total: leaves.length, passed, failed, skipped };
}

/**
 * When {@code PLAYWRIGHT_MAX_FAILURES=1}, Playwright marks not-yet-run tests as skipped in JSON stats.
 * {@code E2E_FAIL_ON_SKIPS} targets intentional {@code test.skip()} (false greens), not that abort behavior.
 */
export function shouldFailOnExplicitSkips(summary, env = process.env) {
  if (env.E2E_FAIL_ON_SKIPS !== "1") {
    return false;
  }
  if (!summary || summary.skipped <= 0) {
    return false;
  }
  if (summary.failed > 0) {
    return false;
  }
  return true;
}
