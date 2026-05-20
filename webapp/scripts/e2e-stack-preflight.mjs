#!/usr/bin/env node
/**
 * Fast fail (<60s) when backend or web UI are unreachable before a heavy Playwright suite.
 * Set E2E_SKIP_STACK_PREFLIGHT=1 to skip (offline UI-only runs).
 */
const MAX_WALL_MS = Number.parseInt(process.env.E2E_STACK_PREFLIGHT_MAX_MS ?? "90000", 10);
const PER_REQUEST_MS = Number.parseInt(process.env.E2E_STACK_PREFLIGHT_REQUEST_MS ?? "12000", 10);
const PRODUCT_PREFIX = (process.env.NEXT_PUBLIC_RAG_API_PREFIX ?? "/api/v5").replace(/\/$/, "");
const SEED_EMAIL = process.env.E2E_SEED_EMAIL ?? process.env.INTEGRATION_LOGIN_EMAIL ?? "dev@local.test";
const SEED_PASSWORD = process.env.E2E_SEED_PASSWORD ?? process.env.INTEGRATION_LOGIN_PASSWORD ?? "dev";

if (process.env.E2E_SKIP_STACK_PREFLIGHT === "1") {
  console.log("e2e-stack-preflight: skipped (E2E_SKIP_STACK_PREFLIGHT=1)");
  process.exit(0);
}

const apiBase = (
  process.env.API_BASE_URL ??
  process.env.INTEGRATION_BACKEND_URL ??
  "http://127.0.0.1:9000"
).replace(/\/$/, "");
/** Actuator probes: direct Spring port in CI/local (proxy HTTPS may not expose /actuator). */
const healthBase = (
  process.env.E2E_BACKEND_HEALTH_URL ??
  process.env.INTEGRATION_BACKEND_URL ??
  apiBase
).replace(/\/$/, "");
const webBase = (process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000").replace(/\/$/, "");
/** Direct Next.js port for curl gates (avoids self-signed HTTPS from Node fetch in Docker). */
const webHealthBase = (process.env.E2E_WEB_HEALTH_URL ?? webBase).replace(/\/$/, "");
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
  await fetchWithTimeout(`${healthBase}/actuator/health`, "backend health");
  const readiness = await fetch(`${healthBase}/actuator/health/readiness`, {
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`backend readiness: ${e instanceof Error ? e.message : e}`);
  });
  if (readiness.status !== 200 && readiness.status !== 503) {
    const body = await readiness.text().catch(() => "");
    throw new Error(`backend readiness: HTTP ${readiness.status} ${body.slice(0, 200)}`);
  }

  const loginRes = await fetch(`${healthBase}${PRODUCT_PREFIX}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: SEED_EMAIL, password: SEED_PASSWORD }),
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`seed login: ${e instanceof Error ? e.message : e}`);
  });
  if (loginRes.status !== 200) {
    const body = await loginRes.text().catch(() => "");
    throw new Error(`seed login: HTTP ${loginRes.status} ${body.slice(0, 200)}`);
  }

  await fetchWithTimeout(`${webHealthBase}/en/login`, "web login page");
  await fetchWithTimeout(`${webHealthBase}/en/lab`, "web lab route");
  console.log(
    `e2e-stack-preflight: OK health=${healthBase} api=${apiBase} web=${webBase} seed=${SEED_EMAIL} (${Date.now() - started}ms)`,
  );
}

main().catch((err) => {
  console.error(`e2e-stack-preflight: FAIL ${err instanceof Error ? err.message : err}`);
  console.error(
    "Hint: start Spring (e2e profile) and webapp/proxy, or set E2E_SKIP_STACK_PREFLIGHT=1 for offline UI-only runs.",
  );
  process.exit(1);
});
