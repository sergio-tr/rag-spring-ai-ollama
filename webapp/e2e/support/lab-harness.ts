import { expect, type APIRequestContext, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import type { ActiveLabJobDto } from "@/types/api";
import { actuatorHealthUrl, apiBaseUrl, publicBaseUrl } from "../api/fixtures/env";
import { authHeadersFromPage, loginAsSeedUser, productApiUrl } from "./helpers";

/** Official demo reverse-proxy port per repair plan. */
export const OFFICIAL_PROXY_HTTPS_PORT = "8444";

const ACTIVE_JOB_STATUSES = new Set(["RUNNING", "QUEUED", "CANCELLING", "ACCEPTED"]);
const TERMINAL_JOB_STATUSES = new Set([
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
  "CANCELED",
  "DONE",
  "ERROR",
]);

type LabJobStatusBody = {
  id?: string;
  status?: string;
  terminal?: boolean;
};

export type LabJobCleanupEntry = {
  jobId: string;
  benchmarkKind: string | null;
  status: string;
  cancellable: boolean;
  action: "cancel_requested" | "cancel_skipped" | "already_absent";
};

export type EnsureNoActiveLabJobsResult = {
  initial: ActiveLabJobDto[];
  final: ActiveLabJobDto[];
  actions: LabJobCleanupEntry[];
  log: string[];
};

export type EnsureNoActiveLabJobsOptions = Readonly<{
  timeoutMs?: number;
  pollIntervalMs?: number;
  evidenceDir?: string;
}>;

function harnessEvidenceDir(override?: string): string | undefined {
  const dir =
    override ??
    process.env.E2E_HARNESS_EVIDENCE_DIR ??
    process.env.E2E_EVIDENCE_DIR;
  return dir?.trim() || undefined;
}

export function writeHarnessEvidence(
  filename: string,
  lines: string[],
  evidenceDir?: string,
): void {
  const dir = harnessEvidenceDir(evidenceDir);
  if (!dir) return;
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, filename), `${lines.join("\n")}\n`, "utf8");
}

function isActiveJob(job: ActiveLabJobDto): boolean {
  const st = (job.status ?? "").trim().toUpperCase();
  return ACTIVE_JOB_STATUSES.has(st);
}

function statusFromPoll(body: LabJobStatusBody): string {
  return (body.status ?? "").trim().toUpperCase();
}

function isTerminalPoll(body: LabJobStatusBody): boolean {
  if (body.terminal === true) return true;
  const st = statusFromPoll(body);
  return TERMINAL_JOB_STATUSES.has(st);
}

export async function fetchActiveLabJobsStrict(page: Page): Promise<ActiveLabJobDto[]> {
  const res = await page.request.get(productApiUrl("/lab/jobs/active"), {
    headers: await authHeadersFromPage(page),
  });
  const bodyText = await res.text();
  expect(
    res.status(),
    `GET /lab/jobs/active must return 200 (got ${res.status()}). Body: ${bodyText.slice(0, 400)}. ` +
      `Check API_BASE_URL / PLAYWRIGHT_BASE_URL proxy (:${OFFICIAL_PROXY_HTTPS_PORT}).`,
  ).toBe(200);
  const body = JSON.parse(bodyText) as unknown;
  if (!Array.isArray(body)) {
    throw new Error(`GET /lab/jobs/active: expected JSON array, got ${typeof body}`);
  }
  return body as ActiveLabJobDto[];
}

async function pollLabJobTerminal(
  page: Page,
  jobId: string,
  timeoutMs: number,
): Promise<LabJobStatusBody> {
  const headers = await authHeadersFromPage(page);
  const deadline = Date.now() + timeoutMs;
  let last: LabJobStatusBody = {};
  while (Date.now() < deadline) {
    const res = await page.request.get(productApiUrl(`/lab/jobs/${jobId}`), { headers });
    const text = await res.text();
    if (res.status() === 200) {
      last = JSON.parse(text) as LabJobStatusBody;
      if (isTerminalPoll(last)) return last;
    }
    await page.waitForTimeout(900);
  }
  throw new Error(
    `Lab job ${jobId} did not reach terminal within ${timeoutMs}ms (last status=${last.status ?? "unknown"} terminal=${String(last.terminal)})`,
  );
}

async function requestCancel(page: Page, job: ActiveLabJobDto): Promise<number> {
  return page.request
    .post(productApiUrl(`/lab/jobs/${job.jobId}/cancel`), {
      headers: await authHeadersFromPage(page),
    })
    .then((r) => r.status());
}

/**
 * Lists active Lab jobs, cancels cancellable ones, waits for terminal/absence.
 * Fails with actionable evidence when non-cancellable jobs block new runs.
 */
export async function ensureNoActiveLabJobs(
  page: Page,
  options?: EnsureNoActiveLabJobsOptions,
): Promise<EnsureNoActiveLabJobsResult> {
  const timeoutMs = options?.timeoutMs ?? 120_000;
  const pollIntervalMs = options?.pollIntervalMs ?? 1_000;
  const log: string[] = [];
  const actions: LabJobCleanupEntry[] = [];

  const initial = await fetchActiveLabJobsStrict(page);
  log.push(`initial active jobs: ${initial.length}`);
  for (const j of initial) {
    log.push(`  - ${j.jobId} kind=${j.benchmarkKind ?? "?"} status=${j.status} cancellable=${j.cancellable}`);
  }

  const deadline = Date.now() + timeoutMs;
  let final = initial;

  while (Date.now() < deadline) {
    final = await fetchActiveLabJobsStrict(page);
    const blocking = final.filter(isActiveJob);

    if (blocking.length === 0) {
      log.push("no blocking active jobs remain");
      const result = { initial, final, actions, log };
      writeHarnessEvidence("active-jobs-cleanup.log", log, options?.evidenceDir);
      return result;
    }

    for (const job of blocking) {
      if (!job.cancellable) {
        continue;
      }
      const status = await requestCancel(page, job);
      actions.push({
        jobId: job.jobId,
        benchmarkKind: job.benchmarkKind,
        status: job.status,
        cancellable: true,
        action: "cancel_requested",
      });
      log.push(`POST cancel ${job.jobId} → HTTP ${status}`);
      try {
        await pollLabJobTerminal(page, job.jobId, Math.min(60_000, deadline - Date.now()));
        log.push(`job ${job.jobId} reached terminal after cancel`);
      } catch (e) {
        log.push(`job ${job.jobId} poll after cancel: ${e instanceof Error ? e.message : String(e)}`);
      }
    }

    const stillBlocking = (await fetchActiveLabJobsStrict(page)).filter(isActiveJob);
    const uncancellable = stillBlocking.filter((j) => !j.cancellable);

    if (uncancellable.length > 0) {
      const detail = uncancellable
        .map((j) => `${j.jobId} status=${j.status} kind=${j.benchmarkKind ?? "?"}`)
        .join("; ");
      log.push(`BLOCKED: uncancellable active jobs: ${detail}`);
      writeHarnessEvidence("active-jobs-cleanup.log", log, options?.evidenceDir);
      throw new Error(
        `ensureNoActiveLabJobs: ${uncancellable.length} active job(s) cannot be cancelled (${detail}). ` +
          "Run button will stay disabled until they finish or an operator cancels them in the API.",
      );
    }

    await page.waitForTimeout(pollIntervalMs);
  }

  final = await fetchActiveLabJobsStrict(page);
  const remaining = final.filter(isActiveJob);
  const detail = remaining.map((j) => `${j.jobId} status=${j.status}`).join("; ");
  log.push(`TIMEOUT: still active after ${timeoutMs}ms: ${detail || "(none listed)"}`);
  writeHarnessEvidence("active-jobs-cleanup.log", log, options?.evidenceDir);
  throw new Error(
    `ensureNoActiveLabJobs: timed out after ${timeoutMs}ms with ${remaining.length} active job(s) (${detail}).`,
  );
}

/** Authenticated Lab E2E with no blocking active jobs (call clearActiveProjectForLab first when needed). */
export async function prepareLabAuthenticatedHarness(
  page: Page,
  options?: EnsureNoActiveLabJobsOptions,
): Promise<void> {
  await loginAsSeedUser(page);
  await ensureNoActiveLabJobs(page, options);
}

/**
 * Preflight: stack reachable, seed auth, active jobs API, cleanup, LAB route.
 * Throws on failure (no test.skip).
 */
export async function preflightLabE2eHarness(
  page: Page,
  request: APIRequestContext,
  evidenceDir?: string,
): Promise<string[]> {
  const log: string[] = [];
  const base = publicBaseUrl();
  log.push(`PLAYWRIGHT_BASE_URL=${base}`);
  log.push(`API_BASE_URL=${apiBaseUrl()}`);
  log.push(`actuator=${actuatorHealthUrl("/liveness")}`);

  const liveness = await request.get(actuatorHealthUrl("/liveness"), { timeout: 12_000 });
  expect(liveness.status(), `backend liveness failed: ${await liveness.text()}`).toBe(200);
  log.push("backend /actuator/health/liveness OK");

  const loginPage = await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 15_000 });
  expect(loginPage?.ok(), "webapp /en/login must be reachable").toBeTruthy();
  log.push("webapp /en/login OK");

  await loginAsSeedUser(page);
  log.push("seed user authenticated");

  await fetchActiveLabJobsStrict(page);
  log.push("GET /lab/jobs/active OK (authenticated)");

  const cleanup = await ensureNoActiveLabJobs(page, { evidenceDir });
  log.push(...cleanup.log);

  const labPage = await page.goto("/en/lab", { waitUntil: "domcontentloaded", timeout: 15_000 });
  expect(labPage?.ok(), "webapp /en/lab must load").toBeTruthy();
  await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
    timeout: 12_000,
  });
  await expect(page.getByTestId("lab-overview-compact")).toBeVisible({ timeout: 12_000 });
  await expect(page.getByTestId("lab-workflow-card-llm")).toBeVisible({ timeout: 12_000 });
  log.push("LAB overview page OK");

  writeHarnessEvidence("preflight.log", log, evidenceDir);
  return log;
}

/** Asserts Run control is enabled (no silent 409 from stale active job). */
export async function assertLabRunButtonEnabled(
  page: Page,
  testId: "lab-llm-run" | "lab-embedding-run" | "lab-rag-run",
): Promise<void> {
  const runButton = page.getByTestId(testId);
  await expect(runButton, `${testId} must be enabled — active job may still block Run`).toBeEnabled({
    timeout: 30_000,
  });
}
