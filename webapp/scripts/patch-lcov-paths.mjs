import fs from "node:fs";
import path from "node:path";

/**
 * SonarCloud resolves LCOV SF paths relative to the repository root.
 * Vitest writes paths relative to the webapp package (e.g. "SF:src/..."),
 * which Sonar cannot map to "webapp/src/..." and reports 0% coverage.
 *
 * This script rewrites LCOV "SF:" paths into repo-root relative paths Sonar can map.
 *
 * Goals:
 * - `SF:src/...`                   -> `SF:webapp/src/...`
 * - `SF:/abs/path/.../webapp/src`  -> `SF:webapp/src/...`
 * - `SF:/abs/path/.../src/...`     -> `SF:webapp/src/...` (Vitest sometimes reports relative to package root)
 *
 * It is intentionally conservative: only touches "SF:" lines that refer to a file under `src/`.
 */
const repoRoot = process.cwd();
const lcovPath = path.join(repoRoot, "coverage", "lcov.info");

if (!fs.existsSync(lcovPath)) {
  console.error(`lcov not found at ${lcovPath}`);
  process.exit(1);
}

const raw = fs.readFileSync(lcovPath, "utf8");
function patchSf(line) {
  if (!line.startsWith("SF:")) return line;
  const rawPath = line.slice("SF:".length);
  const p = rawPath.replaceAll("\\", "/");

  // Already normalized (repo-root relative).
  if (p.startsWith("webapp/src/")) return `SF:${p}`;

  // Absolute path that contains /webapp/src/...
  const webappIdx = p.lastIndexOf("/webapp/src/");
  if (webappIdx >= 0) {
    return `SF:webapp/src/${p.slice(webappIdx + "/webapp/src/".length)}`;
  }

  // Relative-to-package root paths (src/...).
  if (p.startsWith("src/")) {
    return `SF:webapp/${p}`;
  }

  // Absolute path that ends up pointing to /src/... (rare but seen in some Vitest/Vite setups).
  const srcIdx = p.lastIndexOf("/src/");
  if (srcIdx >= 0) {
    return `SF:webapp/src/${p.slice(srcIdx + "/src/".length)}`;
  }

  return line;
}

const next = raw.split("\n").map(patchSf).join("\n");

fs.writeFileSync(lcovPath, next, "utf8");
console.log("Patched LCOV paths for SonarCloud.");

