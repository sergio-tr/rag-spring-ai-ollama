import { execSync } from "node:child_process";
import * as fs from "node:fs";
import { expect, type Page } from "@playwright/test";
import { authHeadersFromPage, productApiUrl } from "./helpers";
import { sampleTextFilePath } from "../fixtures/documents";
import type { ActiveLabJobDto } from "@/types/api";
import {
  ensureNoActiveLabJobs,
  fetchActiveLabJobsStrict,
  prepareLabAuthenticatedHarness,
} from "./lab-harness";

/** Clears persisted active project and LAB draft/session state so runs are projectless and deterministic. */
export async function clearActiveProjectForLab(page: Page): Promise<void> {
  await page.addInitScript(() => {
    try {
      const raw = localStorage.getItem("rag-app");
      if (raw) {
        const o = JSON.parse(raw) as Record<string, unknown>;
        if (o && typeof o === "object") {
          o.state = { ...(o.state as Record<string, unknown>), activeProject: null };
          localStorage.setItem("rag-app", JSON.stringify(o));
        }
      }
      const keysToRemove: string[] = [];
      for (let i = 0; i < localStorage.length; i += 1) {
        const key = localStorage.key(i);
        if (key && (key.startsWith("rag-lab-form-v1:") || key.startsWith("rag-lab-jobs"))) {
          keysToRemove.push(key);
        }
      }
      for (const key of keysToRemove) {
        localStorage.removeItem(key);
      }
      sessionStorage.removeItem("rag-lab-jobs");
    } catch {
      /* ignore */
    }
  });
}

export async function gotoLabEvaluationPage(page: Page, segment: "rag" | "llm" | "embedding"): Promise<void> {
  await page.goto(`/en/lab/evaluation/${segment}`, { waitUntil: "domcontentloaded", timeout: 15_000 });
  await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
    timeout: 12_000,
  });
}

/** Fast-fail when dataset select is missing (stack or feature flag). */
export async function assertLabDatasetControlsVisible(page: Page): Promise<void> {
  const datasetSelect = page.getByTestId("lab-benchmark-dataset-select");
  await expect(datasetSelect, "lab-benchmark-dataset-select must exist").toBeVisible({ timeout: 12_000 });
}

export async function labDatasetRunnable(page: Page, timeoutMs = 25_000): Promise<boolean> {
  const datasetSelect = page.getByTestId("lab-benchmark-dataset-select");
  try {
    await expect
      .poll(
        async () => {
          if (await page.getByTestId("lab-benchmark-needs-dataset-warn").isVisible().catch(() => false)) {
            return false;
          }
          const value = await datasetSelect.inputValue().catch(() => "");
          return value.trim().length > 0;
        },
        { timeout: timeoutMs, intervals: [250, 750, 1500] },
      )
      .toBe(true);
    return true;
  } catch {
    return false;
  }
}

/** UI phases that indicate the job panel is showing meaningful watch state (SSE or terminal). */
export const LAB_JOB_ACTIVE_UI_PHASES = new Set([
  "connecting",
  "live",
  "reconnecting",
  "resumed",
  "queued",
  "running",
  "completed",
  "failed",
  "cancelled",
  "unknown_running",
  "stopped_waiting",
]);

export async function assertLabJobPanelShowsActivePhase(page: Page, timeoutMs = 60_000): Promise<void> {
  const jobPanel = page.getByTestId("lab-job-panel");
  await expect(jobPanel).toBeVisible({ timeout: 30_000 });
  await expect
    .poll(
      async () => {
        const phase = await jobPanel.getAttribute("data-lab-job-ui-phase");
        if (phase && LAB_JOB_ACTIVE_UI_PHASES.has(phase)) {
          return true;
        }
        const text = (await jobPanel.innerText()) ?? "";
        return /live|en vivo|connecting|conectando|reconnecting|reconectando|queued|en cola|running|ejecut|progress|en curso|completed|completado|resumed|reanudado/i.test(
          text,
        );
      },
      { timeout: timeoutMs, intervals: [500, 1500] },
    )
    .toBe(true);
}

export const FORBIDDEN_LAB_UI_PATTERNS: RegExp[] = [
  /POST JSON/i,
  /canonical benchmark API/i,
  /Lab API —/i,
  /POST \/api/i,
  /GET \/api/i,
  /Stopped watching here/i,
  /Stopped waiting — the server job/i,
  /Status poll:/i,
  /Live stream:/i,
  /datasetId.*projectId.*llmModelId/i,
  /embeddingDownstreamRag/i,
  /SSE:\s*\/lab/i,
  /\/api\/v5\/lab\/jobs\//i,
];

export async function assertNoForbiddenLabCopy(page: Page): Promise<void> {
  const main = page.locator("main");
  await expect(main).toBeVisible({ timeout: 8_000 });
  const text = (await main.innerText().catch(() => "")) ?? "";
  for (const re of FORBIDDEN_LAB_UI_PATTERNS) {
    expect(text, `Forbidden copy matched ${re}`).not.toMatch(re);
  }
}

export type LabTerminalOutcome = "results" | "comparison" | "job_done" | "job_running" | "error";

export async function pollLabTerminalOutcome(
  page: Page,
  timeoutMs: number,
): Promise<LabTerminalOutcome> {
  const resultsPanel = page.getByTestId("lab-benchmark-results-panel");
  const comparisonPanel = page.getByTestId("lab-campaign-comparison-panel");
  const jobPanel = page.getByTestId("lab-job-panel");
  const errorAlert = page.locator('[data-slot="card"]').getByRole("alert").first();

  let outcome: LabTerminalOutcome = "job_running";
  await expect
    .poll(
      async () => {
        if (await resultsPanel.isVisible().catch(() => false)) {
          outcome = "results";
          return true;
        }
        if (await comparisonPanel.isVisible().catch(() => false)) {
          outcome = "comparison";
          return true;
        }
        if (await errorAlert.isVisible().catch(() => false)) {
          outcome = "error";
          return true;
        }
        const jobText = (await jobPanel.textContent().catch(() => "")) ?? "";
        if (/SUCCEEDED|COMPLETED|DONE/i.test(jobText)) {
          outcome = "job_done";
          return true;
        }
        if (/FAILED|CANCELLED/i.test(jobText)) {
          outcome = "error";
          return true;
        }
        if (await jobPanel.isVisible().catch(() => false)) {
          outcome = "job_running";
          return false;
        }
        return false;
      },
      { timeout: timeoutMs, intervals: [400, 1200, 2400] },
    )
    .toBe(true)
    .catch(() => undefined);

  return outcome;
}

/** Checks the first N models in the LLM checkbox group (comparison runs). */
export async function selectLlmModelsForComparison(page: Page, count: number): Promise<boolean> {
  const group = page.getByTestId("lab-benchmark-llm-models-group");
  if (!(await group.isVisible().catch(() => false))) return false;
  const boxes = group.locator('input[type="checkbox"]:not(:disabled)');
  const n = await boxes.count();
  if (n < count) return false;
  for (let i = 0; i < count; i += 1) {
    await boxes.nth(i).check();
  }
  return true;
}

/**
 * Models incompatible with the fixed 1024-dim vector_store.embedding column:
 * - nomic-embed-text (768-dim)
 * - qwen3-embedding (4096-dim on current Ollama builds)
 */
const EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN = /nomic-embed|qwen3-embedding/i;

/** Canonical 1024-dim pair for embedding campaign closure E2E (both probe as 1024 in Ollama). */
export const EMBEDDING_CAMPAIGN_PREFERRED_MODEL_IDS = [
  "mxbai-embed-large:latest",
  "bge-m3:latest",
] as const;

function ensureBgeM3EmbeddingAllowlisted(): void {
  const container = process.env.E2E_POSTGRES_DOCKER_CONTAINER ?? "docker-postgres-1";
  const db = process.env.POSTGRES_DB ?? "vectordb";
  const user = process.env.POSTGRES_USER ?? "postgres";
  execSync(
    `docker exec ${container} psql -U ${user} -d ${db} -v ON_ERROR_STOP=1 -c ` +
      JSON.stringify(
        "INSERT INTO allowed_model (name, type, in_allowlist, available, display_name) " +
          "VALUES ('bge-m3:latest', 'EMBEDDING', TRUE, FALSE, 'BGE-M3') " +
          "ON CONFLICT (name, type) DO UPDATE SET in_allowlist = EXCLUDED.in_allowlist, " +
          "display_name = COALESCE(allowed_model.display_name, EXCLUDED.display_name);",
      ),
    { stdio: "pipe" },
  );
}

export async function fetchSelectableEmbeddingModelIds(page: Page): Promise<string[]> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl("/models?type=EMBEDDING"), { headers });
  expect(res.ok(), await res.text()).toBeTruthy();
  const rows = (await res.json()) as Array<{ modelId: string }>;
  return rows.map((r) => r.modelId);
}

function filterCampaignCompatibleEmbeddingIds(modelIds: string[]): string[] {
  return modelIds.filter((id) => id.trim() !== "" && !EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN.test(id));
}

function pullOllamaModelViaDockerExec(modelName: string): void {
  const container = process.env.E2E_OLLAMA_DOCKER_EXEC_CONTAINER ?? "docker-backend-dev-1";
  const ollamaUrl = process.env.E2E_OLLAMA_INTERNAL_URL ?? "http://host.docker.internal:11434";
  const payload = JSON.stringify({ name: modelName });
  execSync(
    `docker exec ${container} curl -sf -X POST ${ollamaUrl}/api/pull -H "Content-Type: application/json" -d ${JSON.stringify(payload)}`,
    { stdio: "pipe", timeout: 600_000, maxBuffer: 64 * 1024 * 1024 },
  );
}

/**
 * Ensures at least {@code minCount} embedding models (1024-dim) are installed and allowlisted.
 * Pulls missing preferred models via Ollama when the demo Docker stack is available.
 */
export async function ensureEmbeddingCampaignModelsReady(
  page: Page,
  minCount = 2,
): Promise<string[]> {
  const preferred = [...EMBEDDING_CAMPAIGN_PREFERRED_MODEL_IDS];
  let compatible = filterCampaignCompatibleEmbeddingIds(await fetchSelectableEmbeddingModelIds(page));

  if (compatible.length < minCount) {
    try {
      ensureBgeM3EmbeddingAllowlisted();
    } catch {
      /* allowlist bootstrap is best-effort when Postgres is not reachable from the test runner */
    }
    compatible = filterCampaignCompatibleEmbeddingIds(await fetchSelectableEmbeddingModelIds(page));
  }

  if (compatible.length < minCount) {
    for (const model of preferred) {
      if (compatible.includes(model)) continue;
      try {
        pullOllamaModelViaDockerExec(model);
      } catch {
        /* Ollama pull is best-effort; API poll below fails with a clear message if still missing */
      }
    }
    await expect
      .poll(
        async () => {
          compatible = filterCampaignCompatibleEmbeddingIds(await fetchSelectableEmbeddingModelIds(page));
          return compatible.length;
        },
        { timeout: 120_000, intervals: [2000, 5000, 10_000] },
      )
      .toBeGreaterThanOrEqual(minCount)
      .catch(() => undefined);
    compatible = filterCampaignCompatibleEmbeddingIds(await fetchSelectableEmbeddingModelIds(page));
  }

  expect(
    compatible.length,
    `Need at least ${minCount} embedding models compatible with 1024-dim vector store (exclude nomic/qwen3). ` +
      `Found: [${compatible.join(", ") || "none"}]. Ensure ${preferred.join(", ")} are allowlisted and installed in Ollama.`,
  ).toBeGreaterThanOrEqual(minCount);

  const picked = preferred.filter((id) => compatible.includes(id));
  return (picked.length >= minCount ? picked : compatible).slice(0, minCount);
}

/** Selects embedding models by checkbox test id (lab-benchmark-embedding-models-{modelId}). */
export async function selectEmbeddingModelsByIds(page: Page, modelIds: string[]): Promise<void> {
  const prefix = "lab-benchmark-embedding-models";
  const group = page.getByTestId(`${prefix}-group`);
  await expect(group).toBeVisible({ timeout: 20_000 });
  for (const modelId of modelIds) {
    const box = page.getByTestId(`${prefix}-${modelId}`);
    await expect(box, `Embedding model checkbox missing: ${modelId}`).toBeVisible({ timeout: 15_000 });
    await box.check();
  }
}

/** Checks up to N embedding models; skips tags known to mismatch a 1024-dim vector store (e.g. nomic 768). */
export async function selectEmbeddingModelsForComparison(page: Page, count: number): Promise<boolean> {
  const group = page.getByTestId("lab-benchmark-embedding-models-group");
  if (!(await group.isVisible().catch(() => false))) return false;
  const boxes = group.locator('input[type="checkbox"]:not(:disabled)');
  const n = await boxes.count();
  let checked = 0;
  for (let i = 0; i < n && checked < count; i += 1) {
    const testId = (await boxes.nth(i).getAttribute("data-testid")) ?? "";
    if (EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN.test(testId)) continue;
    await boxes.nth(i).check();
    checked += 1;
  }
  return checked >= count;
}

/** Ensures at least one LLM model is selected when the checkbox group is shown (avoids stale-draft blocks). */
export async function ensureFirstLlmModelSelectedForRun(page: Page): Promise<void> {
  const group = page.getByTestId("lab-benchmark-llm-models-group");
  if (!(await group.isVisible().catch(() => false))) return;
  const checked = group.locator('input[type="checkbox"]:checked:not(:disabled)');
  if ((await checked.count()) > 0) return;
  const first = group.locator('input[type="checkbox"]:not(:disabled)').first();
  if (await first.isVisible().catch(() => false)) {
    await first.check();
  }
}

/** Fails fast when a Lab run POST returns a permission error instead of opening the job panel. */
export async function assertLabRunStarted(page: Page): Promise<void> {
  await expect(page.getByText(/Insufficient permissions for this action/i)).toHaveCount(0);
  const blockedByOtherJob = page.getByText(/Another Lab evaluation is already running/i);
  await expect(
    blockedByOtherJob,
    "Another active Lab job blocks Run — cancel jobs (ensureNoActiveLabJobs) or run this file serially",
  ).toHaveCount(0);
  await expect(page.getByText(/Live stream configuration error/i)).toHaveCount(0);
  await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });
}

export {
  assertLabRunButtonEnabled,
  ensureNoActiveLabJobs,
  fetchActiveLabJobsStrict,
  prepareLabAuthenticatedHarness,
  preflightLabE2eHarness,
  writeHarnessEvidence,
} from "./lab-harness";

/** Full Lab E2E setup: clear persisted state, auth, cancel/wait active jobs. */
export async function prepareLabE2eTest(page: Page): Promise<void> {
  await clearActiveProjectForLab(page);
  await prepareLabAuthenticatedHarness(page);
}

/** Cancels active jobs and waits for terminal; fails if cleanup cannot complete. */
export async function cancelAllActiveLabJobs(page: Page): Promise<void> {
  await ensureNoActiveLabJobs(page);
}

export async function fetchActiveLabJobs(page: Page): Promise<ActiveLabJobDto[]> {
  try {
    return await fetchActiveLabJobsStrict(page);
  } catch {
    return [];
  }
}

type EvaluationCorpusSummary = {
  id: string;
  readyCount: number;
  documentCount: number;
};

/** Creates/uploads evaluation corpus via API, injects draft corpusId, waits for READY documents. */
export async function ensureLabEvaluationCorpusReadyViaApi(
  page: Page,
  draftKind: "EMBEDDING_RETRIEVAL" | "RAG_PRESET_END_TO_END" = "EMBEDDING_RETRIEVAL",
): Promise<string> {
  const headers = await authHeadersFromPage(page);
  const createRes = await page.request.post(productApiUrl("/lab/evaluation-corpora"), {
    headers: { ...headers, "Content-Type": "application/json", Accept: "application/json" },
    data: { name: `e2e-lab-corpus-${Date.now()}` },
  });
  expect([200, 201], await createRes.text()).toContain(createRes.status());
  const created = (await createRes.json()) as EvaluationCorpusSummary;
  const corpusId = created.id;

  const filePath = sampleTextFilePath();
  const uploadRes = await page.request.post(
    productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/documents`),
    {
      headers,
      multipart: {
        files: {
          name: "sample.txt",
          mimeType: "text/plain",
          buffer: fs.readFileSync(filePath),
        },
      },
    },
  );
  expect([200, 201], await uploadRes.text()).toContain(uploadRes.status());

  let ready = false;
  await expect
    .poll(
      async () => {
        const res = await page.request.get(
          productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}`),
          { headers },
        );
        if (!res.ok()) return false;
        const summary = (await res.json()) as EvaluationCorpusSummary;
        return summary.readyCount >= 1;
      },
      { timeout: 90_000, intervals: [1000, 2500, 5000] },
    )
    .toBe(true)
    .then(() => {
      ready = true;
    })
    .catch(() => undefined);

  if (!ready) {
    const projectsRes = await page.request.get(productApiUrl("/projects?page=0&size=20"), { headers });
    expect(projectsRes.ok(), await projectsRes.text()).toBeTruthy();
    const projectsBody = (await projectsRes.json()) as { items?: Array<{ id: string; name?: string }> };
    const project =
      projectsBody.items?.find((p) => /default/i.test(p.name ?? "")) ?? projectsBody.items?.[0];
    expect(project?.id, "Need a project with documents to attach to evaluation corpus").toBeTruthy();

    const docsRes = await page.request.get(
      productApiUrl(`/projects/${encodeURIComponent(project!.id)}/documents`),
      { headers },
    );
    expect(docsRes.ok(), await docsRes.text()).toBeTruthy();
    const docs = (await docsRes.json()) as Array<{ id: string; status: string; corpusScope?: string }>;
    const sharedIds = docs
      .filter((d) => d.status === "READY" && (d.corpusScope === "PROJECT_SHARED" || !d.corpusScope))
      .map((d) => d.id)
      .slice(0, 3);
    expect(sharedIds.length, "Need READY project documents for embedding corpus").toBeGreaterThan(0);

    const attachRes = await page.request.post(
      productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/documents/from-project`),
      {
        headers: { ...headers, "Content-Type": "application/json", Accept: "application/json" },
        data: { projectId: project!.id, documentIds: sharedIds },
      },
    );
    expect(attachRes.ok(), await attachRes.text()).toBeTruthy();
  }

  await expect
    .poll(
      async () => {
        const res = await page.request.get(
          productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}`),
          { headers },
        );
        if (!res.ok()) return false;
        const summary = (await res.json()) as EvaluationCorpusSummary;
        return summary.readyCount >= 1;
      },
      { timeout: 120_000, intervals: [1000, 2500, 5000] },
    )
    .toBe(true);

  await page.evaluate(
    ({ id, kind }) => {
      const key = `lab:evaluation-draft:v1:${kind}`;
      const raw = localStorage.getItem(key);
      const base = raw ? (JSON.parse(raw) as Record<string, unknown>) : { v: 1 };
      base.corpusId = id;
      localStorage.setItem(key, JSON.stringify(base));
    },
    { id: corpusId, kind: draftKind },
  );
  return corpusId;
}

/** Waits until exactly one active job exists for the benchmark kind. */
export async function waitForSingleActiveLabJob(
  page: Page,
  benchmarkKind: string,
  timeoutMs = 90_000,
): Promise<ActiveLabJobDto> {
  let job: ActiveLabJobDto | undefined;
  await expect
    .poll(
      async () => {
        const active = await fetchActiveLabJobs(page);
        const filtered = active.filter((j) => j.benchmarkKind === benchmarkKind);
        if (filtered.length === 1) {
          job = filtered[0];
          return true;
        }
        return false;
      },
      { timeout: timeoutMs, intervals: [400, 1200, 2400] },
    )
    .toBe(true);
  return job!;
}

export type LabJobStatusBody = {
  status?: string;
  campaignId?: string | null;
  totalItems?: number | null;
  completedItems?: number | null;
  result?: Record<string, unknown> | null;
};

function campaignFieldsFromJobStatus(raw: LabJobStatusBody): {
  campaignId: string | null;
  totalItems: number | null;
} {
  const result = raw.result ?? {};
  const campaignId =
    (typeof result.campaignId === "string" ? result.campaignId : null) ??
    (typeof raw.campaignId === "string" ? raw.campaignId : null);
  const totalRaw = result.totalItems ?? raw.totalItems;
  const totalItems = typeof totalRaw === "number" ? totalRaw : null;
  return { campaignId, totalItems };
}

export async function fetchLabJobStatus(page: Page, jobId: string): Promise<LabJobStatusBody> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl(`/lab/jobs/${jobId}`), { headers });
  expect(res.status(), await res.text()).toBe(200);
  const raw = (await res.json()) as LabJobStatusBody;
  const { campaignId, totalItems } = campaignFieldsFromJobStatus(raw);
  return { ...raw, campaignId, totalItems };
}

/** Polls job status until SUCCEEDED or FAILED/CANCELLED. */
export async function pollLabJobTerminal(
  page: Page,
  jobId: string,
  timeoutMs = 240_000,
): Promise<LabJobStatusBody> {
  let latest: LabJobStatusBody = {};
  await expect
    .poll(
      async () => {
        latest = await fetchLabJobStatus(page, jobId);
        const status = (latest.status ?? "").toUpperCase();
        return status === "SUCCEEDED" || status === "FAILED" || status === "CANCELLED";
      },
      { timeout: timeoutMs, intervals: [800, 2000, 4000] },
    )
    .toBe(true);
  return latest;
}

export type BenchmarkRunRequestBody = {
  embeddingModelIds?: string[];
  indexSnapshotIds?: string[];
  llmModelIds?: string[];
  datasetId?: string;
  corpusId?: string;
  projectId?: string | null;
  experimentalPresetCodes?: string[];
};

/** Captures POST /lab/benchmarks/{kind}/runs request body and accepted JSON. */
export function trackBenchmarkCampaignAccepted(page: Page): {
  readonly accepted: { campaignId?: string | null; totalItems?: number | null; evaluationRunId?: string };
  readonly request: BenchmarkRunRequestBody;
} {
  const state: { campaignId?: string | null; totalItems?: number | null; evaluationRunId?: string } = {};
  const request: BenchmarkRunRequestBody = {};
  page.on("request", (req) => {
    if (req.method() !== "POST") return;
    if (!/\/lab\/benchmarks\/[^/]+\/runs/.test(req.url())) return;
    const body = req.postDataJSON() as Record<string, unknown> | null;
    if (!body) return;
    if (Array.isArray(body.embeddingModelIds)) {
      request.embeddingModelIds = body.embeddingModelIds as string[];
    }
    if (Array.isArray(body.indexSnapshotIds)) {
      request.indexSnapshotIds = body.indexSnapshotIds as string[];
    }
    if (Array.isArray(body.llmModelIds)) {
      request.llmModelIds = body.llmModelIds as string[];
    }
    if (typeof body.datasetId === "string") request.datasetId = body.datasetId;
    if (typeof body.corpusId === "string") request.corpusId = body.corpusId;
    if (body.projectId === null || typeof body.projectId === "string") {
      request.projectId = body.projectId as string | null;
    }
    if (Array.isArray(body.experimentalPresetCodes)) {
      request.experimentalPresetCodes = body.experimentalPresetCodes as string[];
    }
  });
  page.on("response", (res) => {
    void (async () => {
      if (res.request().method() !== "POST") return;
      if (!/\/lab\/benchmarks\/[^/]+\/runs/.test(res.url())) return;
      if (!res.ok()) return;
      const body = (await res.json().catch(() => null)) as {
        campaignId?: string | null;
        totalItems?: number | null;
        evaluationRunId?: string;
      } | null;
      if (!body) return;
      if (body.campaignId) state.campaignId = body.campaignId;
      if (body.totalItems != null) state.totalItems = body.totalItems;
      if (body.evaluationRunId) state.evaluationRunId = body.evaluationRunId;
    })();
  });
  return { accepted: state, request };
}

export async function fetchCampaignSummary(
  page: Page,
  campaignId: string,
): Promise<Record<string, unknown>> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl(`/lab/campaigns/${encodeURIComponent(campaignId)}`), {
    headers,
  });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as Record<string, unknown>;
}

/** Records browser SSE responses for /lab/jobs/{id}/events (do not read the full body). */
export function trackLabJobSseResponses(page: Page): {
  readonly responses: Array<{ url: string; status: number; contentType: string }>;
} {
  const responses: Array<{ url: string; status: number; contentType: string }> = [];
  page.on("response", (res) => {
    const url = res.url();
    if (!url.includes("/lab/jobs/") || !url.includes("/events")) return;
    responses.push({
      url,
      status: res.status(),
      contentType: res.headers()["content-type"] ?? "",
    });
  });
  return { responses };
}

export async function downloadCampaignExportJson(
  page: Page,
  campaignId: string,
  kind: "campaign-items.json" | "campaign-summary.json" | "mvp/items.json" = "campaign-items.json",
): Promise<Record<string, unknown>> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(
    productApiUrl(`/lab/campaigns/${encodeURIComponent(campaignId)}/export/${kind}`),
    { headers },
  );
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as Record<string, unknown>;
}

export async function downloadCampaignExportText(
  page: Page,
  campaignId: string,
  kind: "items.csv" | "summary.csv",
): Promise<string> {
  const headers = { ...(await authHeadersFromPage(page)), Accept: "text/csv,*/*" };
  const res = await page.request.get(
    productApiUrl(`/lab/campaigns/${encodeURIComponent(campaignId)}/export/${kind}`),
    { headers },
  );
  expect(res.status(), await res.text()).toBe(200);
  return await res.text();
}
