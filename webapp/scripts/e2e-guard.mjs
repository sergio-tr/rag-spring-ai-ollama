#!/usr/bin/env node
import { spawnSync } from "node:child_process";

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error("Usage: node scripts/e2e-guard.mjs <playwright test args>");
  process.exit(2);
}

const result = spawnSync("npx", ["playwright", "test", "--list", ...args], {
  cwd: process.cwd(),
  encoding: "utf8",
  stdio: ["ignore", "pipe", "pipe"],
  shell: process.platform === "win32",
});

process.stdout.write(result.stdout);
process.stderr.write(result.stderr);

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

const output = `${result.stdout}\n${result.stderr}`;
const match = output.match(/Total:\s+(\d+)\s+tests?/i);
const total = match ? Number.parseInt(match[1], 10) : Number.NaN;

if (!Number.isFinite(total) || total <= 0) {
  console.error("E2E guard failed: Playwright collected zero tests for the requested critical suite.");
  process.exit(1);
}

console.log(`E2E guard OK: ${total} test(s) collected.`);
