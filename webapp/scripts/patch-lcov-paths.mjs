import fs from "node:fs";
import path from "node:path";

/**
 * SonarCloud resolves LCOV SF paths relative to the repository root.
 * Vitest writes paths relative to the webapp package (e.g. "SF:src/..."),
 * which Sonar cannot map to "webapp/src/..." and reports 0% coverage.
 *
 * This script rewrites:
 *   SF:src/...  -> SF:webapp/src/...
 *
 * It is intentionally conservative: only touches "SF:" lines that start with "src/".
 */
const repoRoot = process.cwd();
const lcovPath = path.join(repoRoot, "coverage", "lcov.info");

if (!fs.existsSync(lcovPath)) {
  console.error(`lcov not found at ${lcovPath}`);
  process.exit(1);
}

const raw = fs.readFileSync(lcovPath, "utf8");
const next = raw
  .split("\n")
  .map((line) => {
    if (line.startsWith("SF:src/")) return `SF:webapp/${line.slice("SF:".length)}`;
    return line;
  })
  .join("\n");

fs.writeFileSync(lcovPath, next, "utf8");
console.log("Patched LCOV paths for SonarCloud.");

