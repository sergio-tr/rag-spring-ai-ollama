#!/usr/bin/env node
/**
 * Fast fail (<60s) when backend or web UI are unreachable before a heavy Playwright suite.
 * Set E2E_SKIP_STACK_PREFLIGHT=1 to skip (offline UI-only runs).
 */
const MAX_WALL_MS = 55_000;
const PER_REQUEST_MS = 12_000;

if (process.env.E2E_SKIP_STACK_PREFLIGHT === "1") {
  console.log("e2e-stack-preflight: skipped (E2E_SKIP_STACK_PREFLIGHT=1)");
  process.exit(0);
}

const apiBase = (
  process.env.API_BASE_URL ??
  process.env.INTEGRATION_BACKEND_URL ??
  "http://127.0.0.1:9000"
).replace(/\/$/, "");
const webBase = (process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000").replace(/\/$/, "");
const deadline = Date.now() + MAX_WALL_MS;

async function fetchWithTimeout(url, label) {
  const remaining = deadline - Date.now();
  if (remaining <= 0) {
    throw new Error(`${label}: overall preflight deadline exceeded (${MAX_WALL_MS}ms)`);
  }
  const timeoutMs = Math.min(PER_REQUEST_MS, remaining);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: controller.signal, redirect: "follow" });
    if (!res.ok) {
      const body = await res.text().catch(() => "");
      throw new Error(`${label}: HTTP ${res.status} ${body.slice(0, 200)}`);
    }
    return res;
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new Error(`${label}: ${msg} (url=${url})`);
  } finally {
    clearTimeout(timer);
  }
}

async function main() {
  const started = Date.now();
  await fetchWithTimeout(`${apiBase}/actuator/health`, "backend health");
  await fetchWithTimeout(`${webBase}/en/login`, "web login page");
  console.log(
    `e2e-stack-preflight: OK backend=${apiBase} web=${webBase} (${Date.now() - started}ms)`,
  );
}

main().catch((err) => {
  console.error(`e2e-stack-preflight: FAIL ${err instanceof Error ? err.message : err}`);
  console.error(
    "Hint: start Spring (e2e profile) and webapp/proxy, or set E2E_SKIP_STACK_PREFLIGHT=1 for offline UI-only runs.",
  );
  process.exit(1);
});
