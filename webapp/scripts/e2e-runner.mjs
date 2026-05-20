#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { countListedTests, summarizePlaywrightJson } from "./e2e-report-summary.mjs";

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error("Usage: node scripts/e2e-runner.mjs <playwright test args>");
  console.error("Example: node scripts/e2e-runner.mjs --project=chromium --grep @preflight --reporter=list,json");
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

const listed = runPlaywright(["--list", ...argsForList(args)]);
process.stdout.write(listed.stdout);
process.stderr.write(listed.stderr);
if (listed.status !== 0) {
  process.exit(listed.status ?? 1);
}

const listedTotal = countListedTests(`${listed.stdout}\n${listed.stderr}`);
if (!Number.isFinite(listedTotal) || listedTotal <= 0) {
  console.error("E2E guard failed: Playwright collected zero tests for the requested suite.");
  console.error("Hint: check --grep/--project paths and that spec files export tests.");
  process.exit(1);
}

const jsonOutputFromEnv = Boolean(process.env.PLAYWRIGHT_JSON_OUTPUT_NAME);
const jsonOutput = resolve(process.env.PLAYWRIGHT_JSON_OUTPUT_NAME ?? "test-results/e2e-results.json");
mkdirSync(dirname(jsonOutput), { recursive: true });
rmSync(jsonOutput, { force: true });

const runArgs = args.some((a) => a.startsWith("--reporter"))
  ? args
  : [...args, "--reporter=list,json"];

const run = runPlaywright(runArgs, {
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
  if (existsSync(jsonOutput)) {
    jsonText = readFileSync(jsonOutput, "utf8");
  } else if (run.stdout.trim()) {
    jsonText = run.stdout.trim();
  }
  if (!jsonText.trim()) {
    throw new Error(`no JSON at ${jsonOutput} and Playwright stdout was empty`);
  }
  summary = summarizePlaywrightJson(JSON.parse(jsonText));
} catch (error) {
  console.error(`E2E guard failed: could not parse Playwright JSON output at ${jsonOutput}: ${error}`);
  process.exit(run.status || 1);
}

console.log(
  `E2E summary: listed=${listedTotal}, jsonTotal=${summary.total}, passed=${summary.passed}, failed=${summary.failed}, skipped=${summary.skipped}, json=${jsonOutput}`,
);

if (summary.total <= 0) {
  console.error(
    `E2E guard failed: Playwright JSON reported zero tests (listed ${listedTotal}). Check reporter includes "json" and PLAYWRIGHT_JSON_OUTPUT_NAME.`,
  );
  process.exit(1);
}
if (summary.total !== listedTotal) {
  console.error(
    `E2E guard warning: listed ${listedTotal} test(s) but JSON summary counted ${summary.total}. Using JSON counts for pass/fail gates.`,
  );
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
