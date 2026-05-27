#!/usr/bin/env node
/**
 * Fast fail (<60s) when backend or web UI are unreachable before a heavy Playwright suite.
 * Set E2E_SKIP_STACK_PREFLIGHT=1 to skip (offline UI-only runs).
 *
 * Demo stack (reverse-proxy): set PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444 (or rely on defaults).
 * Health probes use {origin}/actuator/health — not /api/v5/actuator/health (that path requires JWT).
 */
import { actuatorHealthUrl, resolveE2eBases } from "./e2e-bases.mjs";

const MAX_WALL_MS = Number.parseInt(process.env.E2E_STACK_PREFLIGHT_MAX_MS ?? "90000", 10);
const PER_REQUEST_MS = Number.parseInt(process.env.E2E_STACK_PREFLIGHT_REQUEST_MS ?? "12000", 10);
const PRODUCT_PREFIX = (process.env.NEXT_PUBLIC_RAG_API_PREFIX ?? "/api/v5").replace(/\/$/, "");
const SEED_EMAIL = process.env.E2E_SEED_EMAIL ?? process.env.INTEGRATION_LOGIN_EMAIL ?? "dev@local.test";
const SEED_PASSWORD = process.env.E2E_SEED_PASSWORD ?? process.env.INTEGRATION_LOGIN_PASSWORD ?? "dev";

if (process.env.E2E_SKIP_STACK_PREFLIGHT === "1") {
  console.log("e2e-stack-preflight: skipped (E2E_SKIP_STACK_PREFLIGHT=1)");
  process.exit(0);
}

const { publicBase, apiBase, healthBase, webHealthBase } = resolveE2eBases();
const deadline = Date.now() + MAX_WALL_MS;

async function fetchWithTimeout(url, label, init = {}) {
  const remaining = deadline - Date.now();
  if (remaining <= 0) {
    throw new Error(`${label}: overall preflight deadline exceeded (${MAX_WALL_MS}ms)`);
  }
  const timeoutMs = Math.min(PER_REQUEST_MS, remaining);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...init, signal: controller.signal, redirect: "follow" });
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

  // Liveness only: aggregate /actuator/health may be DOWN when Ollama models are still provisioning.
  await fetchWithTimeout(actuatorHealthUrl(healthBase, "/liveness"), "backend liveness");
  const readinessUrl = actuatorHealthUrl(healthBase, "/readiness");
  const readiness = await fetch(readinessUrl, {
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`backend readiness: ${e instanceof Error ? e.message : e} (url=${readinessUrl})`);
  });
  if (readiness.status !== 200 && readiness.status !== 503) {
    const body = await readiness.text().catch(() => "");
    throw new Error(`backend readiness: HTTP ${readiness.status} ${body.slice(0, 200)}`);
  }

  const loginUrl = `${apiBase}${PRODUCT_PREFIX}/auth/login`;
  const loginRes = await fetch(loginUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: SEED_EMAIL, password: SEED_PASSWORD }),
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`seed login: ${e instanceof Error ? e.message : e} (url=${loginUrl})`);
  });
  if (loginRes.status !== 200) {
    const body = await loginRes.text().catch(() => "");
    throw new Error(`seed login: HTTP ${loginRes.status} ${body.slice(0, 200)} (url=${loginUrl})`);
  }

  await fetchWithTimeout(`${webHealthBase}/en/login`, "web login page");
  await fetchWithTimeout(`${webHealthBase}/en/lab`, "web lab route");
  await fetchWithTimeout(`${webHealthBase}/en/chat`, "web chat route");

  const loginJson = await loginRes.json().catch(() => ({}));
  const accessToken = loginJson.accessToken;
  if (!accessToken) {
    throw new Error("seed login: missing accessToken in response");
  }
  const activeUrl = `${apiBase}${PRODUCT_PREFIX}/lab/jobs/active`;
  const activeRes = await fetch(activeUrl, {
    headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`lab jobs active: ${e instanceof Error ? e.message : e} (url=${activeUrl})`);
  });
  if (activeRes.status !== 200) {
    const body = await activeRes.text().catch(() => "");
    throw new Error(`lab jobs active: HTTP ${activeRes.status} ${body.slice(0, 200)} (url=${activeUrl})`);
  }

  console.log(
    `e2e-stack-preflight: OK public=${publicBase} health=${healthBase} api=${apiBase} web=${webHealthBase} seed=${SEED_EMAIL} labActiveJobs=200 (${Date.now() - started}ms)`,
  );
}

main().catch((err) => {
  console.error(`e2e-stack-preflight: FAIL ${err instanceof Error ? err.message : err}`);
  console.error(
    "Hint: for Docker demo use PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444 (actuator at /actuator/health on origin, not /api/v5/actuator). " +
      "For CI/direct backend use API_BASE_URL=http://127.0.0.1:9000 and E2E_BACKEND_HEALTH_URL=http://127.0.0.1:9000. " +
      "Offline UI-only: E2E_SKIP_STACK_PREFLIGHT=1.",
  );
  process.exit(1);
});
