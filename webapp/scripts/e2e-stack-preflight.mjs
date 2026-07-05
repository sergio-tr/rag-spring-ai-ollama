#!/usr/bin/env node
/**
 * Fast fail (<60s) when backend or web UI are unreachable before a heavy Playwright suite.
 * Set E2E_SKIP_STACK_PREFLIGHT=1 to skip (offline UI-only runs).
 *
 * Demo stack (reverse-proxy): set PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444 (or rely on defaults).
 * Health probes use {origin}/actuator/health - not /api/v5/actuator/health (that path requires JWT).
 */
import { actuatorHealthUrl, productBasePath, resolveE2eBases } from "./e2e-bases.mjs";

/** Exit code when the stack is offline or missing seed fixtures - Playwright must not start. */
export const E2E_STACK_NOT_READY_EXIT_CODE = 2;

const MAX_WALL_MS = Number.parseInt(process.env.E2E_STACK_PREFLIGHT_MAX_MS ?? "120000", 10);
const PER_REQUEST_MS = Number.parseInt(process.env.E2E_STACK_PREFLIGHT_REQUEST_MS ?? "20000", 10);
const PRODUCT_PREFIX = productBasePath();
const SEED_EMAIL = process.env.E2E_SEED_EMAIL ?? process.env.INTEGRATION_LOGIN_EMAIL ?? "dev@local.test";
const SEED_PASSWORD = process.env.E2E_SEED_PASSWORD ?? process.env.INTEGRATION_LOGIN_PASSWORD ?? "dev";
const SEED_PROJECT_ID =
  process.env.E2E_SEED_PROJECT_ID ?? "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22";

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
    const refused =
      /ECONNREFUSED|fetch failed|ENOTFOUND|ECONNRESET|aborted|timeout/i.test(msg) ||
      (e instanceof Error && e.name === "AbortError");
    const prefix = refused ? "E2E_STACK_NOT_READY" : "E2E_STACK_PREFLIGHT_FAIL";
    throw new Error(`${prefix}: ${label}: ${msg} (url=${url})`);
  } finally {
    clearTimeout(timer);
  }
}

function connectivityError(label, err, url) {
  const msg = err instanceof Error ? err.message : String(err);
  const refused =
    /ECONNREFUSED|fetch failed|ENOTFOUND|ECONNRESET|aborted|timeout/i.test(msg) ||
    (err instanceof Error && err.name === "AbortError");
  const prefix = refused ? "E2E_STACK_NOT_READY" : "E2E_STACK_PREFLIGHT_FAIL";
  return new Error(`${prefix}: ${label}: ${msg} (url=${url})`);
}

async function main() {
  const started = Date.now();

  // Liveness only: aggregate /actuator/health may be DOWN when Ollama models are still provisioning.
  await fetchWithTimeout(actuatorHealthUrl(healthBase, "/liveness"), "backend liveness");
  const readinessUrl = actuatorHealthUrl(healthBase, "/readiness");
  const readiness = await fetch(readinessUrl, {
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw connectivityError("backend readiness", e, readinessUrl);
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
    throw connectivityError("seed login", e, loginUrl);
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

  const authHeaders = { Authorization: `Bearer ${accessToken}`, Accept: "application/json" };

  const modelsUrl = `${apiBase}${PRODUCT_PREFIX}/me/llm/selectable-models?capability=CHAT`;
  const modelsRes = await fetch(modelsUrl, {
    headers: authHeaders,
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`selectable models: ${e instanceof Error ? e.message : e} (url=${modelsUrl})`);
  });
  if (modelsRes.status !== 200) {
    const body = await modelsRes.text().catch(() => "");
    throw new Error(`selectable models: HTTP ${modelsRes.status} ${body.slice(0, 200)}`);
  }
  const modelsJson = await modelsRes.json().catch(() => ({}));
  const selectable = (modelsJson.models ?? []).filter(
    (m) => m.selectable && String(m.modelName ?? "").trim(),
  );
  if (selectable.length < 1) {
    throw new Error("selectable models: no selectable CHAT models for seed user");
  }

  const projectsUrl = `${apiBase}${PRODUCT_PREFIX}/projects?page=0&size=20`;
  const projectsRes = await fetch(projectsUrl, {
    headers: authHeaders,
    signal: AbortSignal.timeout(Math.min(PER_REQUEST_MS, MAX_WALL_MS)),
  }).catch((e) => {
    throw new Error(`seed projects: ${e instanceof Error ? e.message : e} (url=${projectsUrl})`);
  });
  if (projectsRes.status !== 200) {
    const body = await projectsRes.text().catch(() => "");
    throw new Error(`seed projects: HTTP ${projectsRes.status} ${body.slice(0, 200)}`);
  }
  const projectsJson = await projectsRes.json().catch(() => ({}));
  const projectItems = projectsJson.items ?? [];
  if (!projectItems.some((p) => p.id === SEED_PROJECT_ID)) {
    throw new Error(`seed project fixture missing (expected id=${SEED_PROJECT_ID})`);
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
    `e2e-stack-preflight: OK public=${publicBase} health=${healthBase} api=${apiBase} web=${webHealthBase} seed=${SEED_EMAIL} project=${SEED_PROJECT_ID} selectableModels=${selectable.length} labActiveJobs=200 (${Date.now() - started}ms)`,
  );
}

function preflightExitCode(message) {
  return /^E2E_STACK_NOT_READY:/.test(message) ? E2E_STACK_NOT_READY_EXIT_CODE : 1;
}

main().catch((err) => {
  const message = err instanceof Error ? err.message : String(err);
  console.error(`e2e-stack-preflight: FAIL ${message}`);
  console.error(
    "Hint: for Docker demo use PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444 and E2E_PRODUCT_URL=https://127.0.0.1:8444 " +
      "(actuator at /actuator/health on origin, not /api/v5/actuator). " +
      "For CI/direct backend use E2E_PRODUCT_URL=http://127.0.0.1:9000 and E2E_BACKEND_HEALTH_URL=http://127.0.0.1:9000. " +
      "Offline UI-only: E2E_SKIP_STACK_PREFLIGHT=1.",
  );
  process.exit(preflightExitCode(message));
});
