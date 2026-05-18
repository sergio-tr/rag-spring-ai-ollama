#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error("Usage: node scripts/e2e-runner.mjs <playwright test args>");
  process.exit(2);
}

function runPlaywright(extraArgs, options = {}) {
  return spawnSync("npx", ["playwright", "test", ...extraArgs], {
    cwd: process.cwd(),
    encoding: "utf8",
    stdio: options.stdio ?? ["ignore", "pipe", "pipe"],
    env: options.env ?? process.env,
    shell: process.platform === "win32",
  });
}

function argsForList(rawArgs) {
  return rawArgs.filter((arg) => !arg.startsWith("--reporter"));
}

function parseListedTotal(output) {
  const match = output.match(/Total:\s+(\d+)\s+tests?/i);
  return match ? Number.parseInt(match[1], 10) : Number.NaN;
}

function collectTests(node, acc = []) {
  if (node == null || typeof node !== "object") {
    return acc;
  }
  if (Array.isArray(node)) {
    for (const item of node) collectTests(item, acc);
    return acc;
  }
  if (Array.isArray(node.results) && (typeof node.title === "string" || typeof node.outcome === "string")) {
    acc.push(node);
  }
  for (const value of Object.values(node)) {
    collectTests(value, acc);
  }
  return acc;
}

function summarize(json) {
  const tests = collectTests(json);
  const total = tests.length;
  let skipped = 0;
  let passed = 0;
  let failed = 0;
  for (const test of tests) {
    const results = Array.isArray(test.results) ? test.results : [];
    const statuses = results.map((r) => r?.status).filter(Boolean);
    const outcome = typeof test.outcome === "string" ? test.outcome : "";
    if (outcome === "skipped" || (statuses.length > 0 && statuses.every((s) => s === "skipped"))) {
      skipped += 1;
    } else if (outcome === "unexpected" || statuses.some((s) => ["failed", "timedOut", "interrupted"].includes(String(s)))) {
      failed += 1;
    } else {
      passed += 1;
    }
  }
  return { total, passed, failed, skipped };
}

const listed = runPlaywright(["--list", ...argsForList(args)]);
process.stdout.write(listed.stdout);
process.stderr.write(listed.stderr);
if (listed.status !== 0) {
  process.exit(listed.status ?? 1);
}

const listedTotal = parseListedTotal(`${listed.stdout}\n${listed.stderr}`);
if (!Number.isFinite(listedTotal) || listedTotal <= 0) {
  console.error("E2E guard failed: Playwright collected zero tests for the requested suite.");
  process.exit(1);
}

const jsonOutputFromEnv = Boolean(process.env.PLAYWRIGHT_JSON_OUTPUT_NAME);
const jsonOutput = resolve(process.env.PLAYWRIGHT_JSON_OUTPUT_NAME ?? "test-results/e2e-results.json");
mkdirSync(dirname(jsonOutput), { recursive: true });
rmSync(jsonOutput, { force: true });

const run = runPlaywright(args, {
  env: {
    ...process.env,
    PLAYWRIGHT_JSON_OUTPUT_NAME: jsonOutput,
  },
  stdio: ["ignore", "pipe", "pipe"],
});

if (run.stdout.trim() && !jsonOutputFromEnv) {
  writeFileSync(jsonOutput, run.stdout, "utf8");
}
process.stderr.write(run.stderr);

let summary;
try {
  let jsonText = "";
  if (jsonOutputFromEnv && existsSync(jsonOutput)) {
    jsonText = readFileSync(jsonOutput, "utf8");
  } else if (run.stdout.trim()) {
    jsonText = run.stdout.trim();
  } else if (existsSync(jsonOutput)) {
    jsonText = readFileSync(jsonOutput, "utf8");
  }
  summary = summarize(JSON.parse(jsonText));
} catch (error) {
  console.error(`E2E guard failed: could not parse Playwright JSON output at ${jsonOutput}: ${error}`);
  process.exit(run.status || 1);
}

console.log(
  `E2E summary: total=${summary.total}, passed=${summary.passed}, failed=${summary.failed}, skipped=${summary.skipped}, json=${jsonOutput}`,
);

if (summary.total <= 0) {
  console.error("E2E guard failed: Playwright JSON reported zero tests.");
  process.exit(1);
}
if (summary.skipped >= summary.total && summary.passed === 0 && summary.failed === 0) {
  console.error(`E2E guard failed: all collected tests were skipped (${summary.skipped}/${summary.total}).`);
  process.exit(1);
}
if (process.env.E2E_FAIL_ON_SKIPS === "1" && summary.skipped > 0) {
  console.error(`E2E guard failed: E2E_FAIL_ON_SKIPS=1 and ${summary.skipped} test(s) skipped.`);
  process.exit(1);
}

process.exit(run.status ?? 1);
