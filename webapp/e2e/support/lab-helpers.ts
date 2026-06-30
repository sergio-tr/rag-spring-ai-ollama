import { execSync } from "node:child_process";
import * as fs from "node:fs";
import { expect, type Page } from "@playwright/test";
import { authHeadersFromPage, productApiUrl } from "./helpers";
import {
  actaKnowledgeBaseFilePath,
  ragClasspathBootstrapActaFilePath,
  sampleTextFilePath,
} from "../fixtures/documents";
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
        if (
          key &&
          (key.startsWith("rag-lab-form-v1:") ||
            key.startsWith("rag-lab-jobs") ||
            key.startsWith("lab:evaluation-draft:v1:"))
        ) {
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

/** Uploads a document through the knowledge base file input and waits for UI refresh. */
export async function uploadLabCorpusFileViaUi(page: Page, filePath: string): Promise<void> {
  const uploadInput = page.getByTestId("lab-corpus-upload-input");
  await expect(uploadInput).toBeAttached({ timeout: 10_000 });
  await expect
    .poll(async () => (await uploadInput.getAttribute("data-upload-handler-ready")) === "true", {
      timeout: 15_000,
      intervals: [250, 500, 1000],
    })
    .toBe(true);
  await expect
    .poll(async () => !(await uploadInput.isDisabled()), { timeout: 30_000, intervals: [250, 500, 1000] })
    .toBe(true);
  const uploadRequest = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes("/lab/evaluation-corpora/") &&
      response.url().includes("/documents"),
    { timeout: 120_000 },
  );
  await uploadInput.setInputFiles(filePath);
  const response = await uploadRequest;
  expect(response.ok(), await response.text()).toBeTruthy();
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
  /Follow the steps below/i,
  /How to read P0/i,
  /Guided steps/i,
  /\bevaluation corpus\b/i,
  /\bcorpusId\b/i,
  /\bcorpus and snapshot\b/i,
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
  /\bRAG_PRESET_END_TO_END\b/,
  /\bLLM_JUDGE_QA\b/,
  /\bEMBEDDING_RETRIEVAL\b/,
  /\bEMBEDDING_CAMPAIGN_STORE_DIMENSION\b/,
  /Missing preferred/i,
  /missing preferred/i,
  /1024-dimension vector store/i,
  /\bFUTURE_MULTI_TURN_NOT_SELECTABLE\b/,
  /\bREQUIRES_MULTI_TURN\b/,
  /\bLLM_MODEL_BASELINE\b/,
  /\bRAG_PRESET_BENCHMARK\b/,
  /Use active project documents/i,
  /Usar documentos del proyecto activo/i,
  /Select an active project in the header/i,
  /Selecciona un proyecto activo en la cabecera/i,
  /This project has no ready shared documents/i,
  /Este proyecto no tiene documentos compartidos listos/i,
  /active vector index required/i,
  /index snapshot required/i,
  /\bREINDEX_REQUIRED\b/,
  /\bNO_ACTIVE_SNAPSHOT\b/,
  /\bINDEX_PREPARATION_REQUIRED\b/,
  /resolved_config_snapshot/i,
  /knowledge_index_snapshot/i,
  /autogenerated project/i,
  /\bFEATURE_REQUIRES_INDEX\b/,
  /\bevidence\b/i,
  /\bthesis\b/i,
  /do not claim/i,
  /\bM9\b|\bM10\b|\bM11\b|\bM12\b/i,
  /\bTFG\b/i,
  /claim map/i,
  /\.cursor/i,
  /Jaeger verified/i,
  /RAG ladder complete/i,
  /Do not claim/i,
];

export async function collectVisibleMainText(page: Page): Promise<string> {
  return page.locator("main").evaluate((main) => {
    const parts: string[] = [];
    const walker = document.createTreeWalker(main, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
      const textNode = node as Text;
      const parent = textNode.parentElement;
      if (parent) {
        const details = parent.closest("details");
        const inClosedDetails = details && !details.open && !parent.closest("summary");
        const style = window.getComputedStyle(parent);
        const hidden =
          style.display === "none" ||
          style.visibility === "hidden" ||
          style.opacity === "0" ||
          parent.closest("[hidden]") != null ||
          parent.closest('[aria-hidden="true"]') != null;
        if (!inClosedDetails && !hidden) {
          const text = textNode.textContent?.trim();
          if (text) {
            parts.push(text);
          }
        }
      }
      node = walker.nextNode();
    }
    return parts.join("\n");
  });
}

export async function assertNoForbiddenLabCopy(page: Page): Promise<void> {
  const main = page.locator("main");
  await expect(main).toBeVisible({ timeout: 8_000 });
  const text = await collectVisibleMainText(page);
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

/** Canonical LLM tags for comparison closure (Flyway V61 allowlist). */
export const LLM_CAMPAIGN_PREFERRED_MODEL_IDS = [
  "llama3.1:8b",
  "gemma3:4b",
  "mistral:7b",
] as const;

export type LlmModelValidationSnapshot = {
  status: "READY" | "BLOCKED_BY_MODEL_AVAILABILITY";
  selectableLlmModelIds: string[];
  allowlistLlmNames: string[];
  ollamaTags: string[];
  preferred: string[];
  missingPreferred: string[];
  checkedAt: string;
};

function ensureLlmModelsAllowlisted(modelNames: readonly string[]): void {
  const container = process.env.E2E_POSTGRES_DOCKER_CONTAINER ?? "docker-postgres-1";
  const db = process.env.POSTGRES_DB ?? "vectordb";
  const user = process.env.POSTGRES_USER ?? "postgres";
  for (const name of modelNames) {
    execSync(
      `docker exec ${container} psql -U ${user} -d ${db} -v ON_ERROR_STOP=1 -c ` +
        JSON.stringify(
          "INSERT INTO allowed_model (name, type, in_allowlist, available, display_name) " +
            `VALUES ('${name.replace(/'/g, "''")}', 'LLM', TRUE, FALSE, '${name.replace(/'/g, "''")}') ` +
            "ON CONFLICT (name, type) DO UPDATE SET in_allowlist = EXCLUDED.in_allowlist;",
        ),
      { stdio: "pipe" },
    );
  }
}

export async function fetchSelectableLlmModelIds(page: Page): Promise<string[]> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl("/models?type=LLM"), { headers });
  expect(res.ok(), await res.text()).toBeTruthy();
  const rows = (await res.json()) as Array<{ modelId: string }>;
  return rows.map((r) => r.modelId);
}

export async function fetchModelsCatalogSnapshot(page: Page): Promise<{
  reachable: boolean;
  installedModelNames: string[];
  allowlistLlmNames: string[];
  allowlistEmbeddingNames: string[];
}> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl("/models"), { headers });
  expect(res.ok(), await res.text()).toBeTruthy();
  const body = (await res.json()) as {
    reachable?: boolean;
    installedModelNames?: string[];
    entries?: Array<{ name?: string; type?: string; inAllowlist?: boolean; installedInOllama?: boolean }>;
  };
  const entries = body.entries ?? [];
  const allowlistLlmNames = entries
    .filter((e) => e.type === "LLM" && e.inAllowlist)
    .map((e) => String(e.name ?? "").trim())
    .filter(Boolean);
  const allowlistEmbeddingNames = entries
    .filter((e) => e.type === "EMBEDDING" && e.inAllowlist)
    .map((e) => String(e.name ?? "").trim())
    .filter(Boolean);
  return {
    reachable: body.reachable === true,
    installedModelNames: body.installedModelNames ?? [],
    allowlistLlmNames,
    allowlistEmbeddingNames,
  };
}

function listOllamaTagsViaDockerExec(): string[] {
  const container = process.env.E2E_OLLAMA_DOCKER_EXEC_CONTAINER ?? "docker-backend-dev-1";
  const ollamaUrl = process.env.E2E_OLLAMA_INTERNAL_URL ?? "http://host.docker.internal:11434";
  try {
    const raw = execSync(
      `docker exec ${container} curl -sf ${ollamaUrl}/api/tags`,
      { stdio: "pipe", timeout: 30_000, maxBuffer: 8 * 1024 * 1024 },
    ).toString("utf8");
    const parsed = JSON.parse(raw) as { models?: Array<{ name?: string }> };
    return (parsed.models ?? []).map((m) => String(m.name ?? "").trim()).filter(Boolean);
  } catch {
    return [];
  }
}

/** Collects allowlist, Ollama tags, and selectable LLM ids for closure evidence. */
export async function collectLlmModelValidation(page: Page): Promise<LlmModelValidationSnapshot> {
  const preferred = [...LLM_CAMPAIGN_PREFERRED_MODEL_IDS];
  const catalog = await fetchModelsCatalogSnapshot(page);
  const selectableLlmModelIds = await fetchSelectableLlmModelIds(page);
  const ollamaTags = listOllamaTagsViaDockerExec();
  const missingPreferred = preferred.filter((id) => !selectableLlmModelIds.includes(id));
  const status: LlmModelValidationSnapshot["status"] =
    selectableLlmModelIds.length >= 1 ? "READY" : "BLOCKED_BY_MODEL_AVAILABILITY";
  return {
    status,
    selectableLlmModelIds,
    allowlistLlmNames: catalog.allowlistLlmNames,
    ollamaTags: ollamaTags.length > 0 ? ollamaTags : catalog.installedModelNames,
    preferred,
    missingPreferred,
    checkedAt: new Date().toISOString(),
  };
}

/**
 * Ensures at least {@code minCount} LLM models are allowlisted and installed in Ollama.
 * Pulls missing preferred models when the demo Docker stack is available.
 */
export async function ensureLlmCampaignModelsReady(page: Page, minCount = 2): Promise<string[]> {
  const preferred = [...LLM_CAMPAIGN_PREFERRED_MODEL_IDS];
  try {
    ensureLlmModelsAllowlisted(preferred);
  } catch {
    /* allowlist bootstrap is best-effort when Postgres is not reachable from the test runner */
  }

  let selectable = await fetchSelectableLlmModelIds(page);
  if (selectable.length < minCount) {
    for (const model of preferred) {
      if (selectable.includes(model)) continue;
      try {
        pullOllamaModelViaDockerExec(model);
      } catch {
        /* pull is best-effort */
      }
    }
    await expect
      .poll(
        async () => {
          selectable = await fetchSelectableLlmModelIds(page);
          return selectable.length;
        },
        { timeout: 600_000, intervals: [2000, 5000, 10_000] },
      )
      .toBeGreaterThanOrEqual(minCount);
    selectable = await fetchSelectableLlmModelIds(page);
  }

  expect(
    selectable.length,
    `BLOCKED_BY_MODEL_AVAILABILITY: need at least ${minCount} installed allowlisted LLM models. ` +
      `Found: [${selectable.join(", ") || "none"}]. Preferred: [${preferred.join(", ")}]. ` +
      `Pull/install missing tags in Ollama and verify allowlist (Flyway V61).`,
  ).toBeGreaterThanOrEqual(minCount);

  const picked = preferred.filter((id) => selectable.includes(id));
  if (picked.length >= minCount) {
    return minCount >= preferred.length ? [...picked] : picked.slice(0, minCount);
  }
  const fallback = selectable.filter((id) => !picked.includes(id as (typeof preferred)[number]));
  return [...picked, ...fallback].slice(0, minCount);
}

/** Selects LLM models by checkbox test id (lab-benchmark-llm-models-{modelId}). */
export async function selectLlmModelsByIds(page: Page, modelIds: string[]): Promise<void> {
  const prefix = "lab-benchmark-llm-models";
  const group = page.getByTestId(`${prefix}-group`);
  await expect(group).toBeVisible({ timeout: 20_000 });
  for (const modelId of modelIds) {
    const box = page.getByTestId(`${prefix}-${modelId}`);
    await expect(box, `LLM model checkbox missing: ${modelId}`).toBeVisible({ timeout: 15_000 });
    await box.check();
  }
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

/** E2E-only bootstrap tags when closure needs ≥2 store-compatible embeddings (not user requirements). */
export const EMBEDDING_E2E_BOOTSTRAP_MODEL_IDS = [
  "mxbai-embed-large:latest",
  "bge-m3:latest",
] as const;

function ensureEmbeddingModelsAllowlisted(modelNames: readonly string[]): void {
  const container = process.env.E2E_POSTGRES_DOCKER_CONTAINER ?? "docker-postgres-1";
  const db = process.env.POSTGRES_DB ?? "vectordb";
  const user = process.env.POSTGRES_USER ?? "postgres";
  for (const name of modelNames) {
    const safeName = name.replace(/'/g, "''");
    execSync(
      `docker exec ${container} psql -U ${user} -d ${db} -v ON_ERROR_STOP=1 -c ` +
        JSON.stringify(
          "INSERT INTO allowed_model (name, type, in_allowlist, available, display_name) " +
            `VALUES ('${safeName}', 'EMBEDDING', TRUE, FALSE, '${safeName}') ` +
            "ON CONFLICT (name, type) DO UPDATE SET in_allowlist = EXCLUDED.in_allowlist;",
        ),
      { stdio: "pipe" },
    );
  }
}

const EMBEDDING_STORE_DIMENSION = 1024;

function probeEmbeddingDimensionViaDockerExec(modelName: string): number | null {
  const container = process.env.E2E_OLLAMA_DOCKER_EXEC_CONTAINER ?? "docker-backend-dev-1";
  const ollamaUrl = process.env.E2E_OLLAMA_INTERNAL_URL ?? "http://host.docker.internal:11434";
  const payload = JSON.stringify({ model: modelName, prompt: "rag-embedding-dimension-probe" });
  try {
    const raw = execSync(
      `docker exec ${container} curl -sf -X POST ${ollamaUrl}/api/embeddings -H "Content-Type: application/json" -d ${JSON.stringify(payload)}`,
      { stdio: "pipe", timeout: 120_000, maxBuffer: 8 * 1024 * 1024 },
    ).toString("utf8");
    const parsed = JSON.parse(raw) as { embedding?: number[] };
    const dims = parsed.embedding?.length ?? 0;
    return dims > 0 ? dims : null;
  } catch {
    return null;
  }
}

export type EmbeddingModelValidationSnapshot = {
  status: "READY" | "BLOCKED_BY_MODEL_AVAILABILITY";
  selectableCompatibleEmbeddingIds: string[];
  allowlistEmbeddingNames: string[];
  ollamaTags: string[];
  excludedIncompatibleTags: string[];
  expectedStoreDimension: number;
  probeByModel: Record<string, { dimension: number | null; probeOk: boolean; dimensionCompatible: boolean }>;
  checkedAt: string;
};

/** Collects allowlist, Ollama tags, dimension probes, and 1024-dim-compatible selectable embeddings. */
export async function collectEmbeddingModelValidation(page: Page): Promise<EmbeddingModelValidationSnapshot> {
  const catalog = await fetchModelsCatalogSnapshot(page);
  const selectableCompatibleEmbeddingIds = await fetchSelectableEmbeddingModelIds(page);
  const ollamaTags = listOllamaTagsViaDockerExec();
  const installed = ollamaTags.length > 0 ? ollamaTags : catalog.installedModelNames;
  const excludedIncompatibleTags = catalog.allowlistEmbeddingNames.filter(
    (name) => !filterCampaignCompatibleEmbeddingIds([name]).includes(name),
  );

  const probeByModel: EmbeddingModelValidationSnapshot["probeByModel"] = {};
  for (const modelId of selectableCompatibleEmbeddingIds) {
    const dimension = installed.some((t) => t === modelId || t.startsWith(modelId.split(":")[0]))
      ? probeEmbeddingDimensionViaDockerExec(modelId)
      : null;
    probeByModel[modelId] = {
      dimension,
      probeOk: dimension != null && dimension > 0,
      dimensionCompatible: dimension === EMBEDDING_STORE_DIMENSION,
    };
  }

  const status: EmbeddingModelValidationSnapshot["status"] =
    selectableCompatibleEmbeddingIds.length >= 1 ? "READY" : "BLOCKED_BY_MODEL_AVAILABILITY";

  return {
    status,
    selectableCompatibleEmbeddingIds,
    allowlistEmbeddingNames: catalog.allowlistEmbeddingNames,
    ollamaTags: installed,
    excludedIncompatibleTags,
    expectedStoreDimension: EMBEDDING_STORE_DIMENSION,
    probeByModel,
    checkedAt: new Date().toISOString(),
  };
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
  const bootstrap = [...EMBEDDING_E2E_BOOTSTRAP_MODEL_IDS];
  let compatible = await fetchSelectableEmbeddingModelIds(page);

  if (compatible.length < minCount) {
    try {
      ensureEmbeddingModelsAllowlisted(bootstrap);
    } catch {
      /* allowlist bootstrap is best-effort when Postgres is not reachable from the test runner */
    }
    compatible = await fetchSelectableEmbeddingModelIds(page);
  }

  if (compatible.length < minCount) {
    for (const model of bootstrap) {
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
          compatible = await fetchSelectableEmbeddingModelIds(page);
          return compatible.length;
        },
        { timeout: 120_000, intervals: [2000, 5000, 10_000] },
      )
      .toBeGreaterThanOrEqual(minCount)
      .catch(() => undefined);
    compatible = await fetchSelectableEmbeddingModelIds(page);
  }

  expect(
    compatible.length,
    `BLOCKED_BY_MODEL_AVAILABILITY: need at least ${minCount} store-compatible embedding models for comparison. ` +
      `Found: [${compatible.join(", ") || "none"}].`,
  ).toBeGreaterThanOrEqual(minCount);

  const picked = bootstrap.filter((id) => compatible.includes(id));
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

/** Dismisses LAB session recovery banner (Stop watching / Dismiss) without blocking the test. */
export async function dismissLabJobSessionBannerIfPresent(page: Page): Promise<void> {
  const stopWatching = page.getByRole("button", {
    name: /stop watching|dejar de seguir|forget job|olvidar trabajo/i,
  });
  const dismiss = page.getByRole("button", { name: /^dismiss$|^descartar$/i });
  if (await stopWatching.first().isVisible().catch(() => false)) {
    await stopWatching.first().click({ timeout: 10_000 }).catch(() => undefined);
    return;
  }
  if (await dismiss.first().isVisible().catch(() => false)) {
    await dismiss.first().click({ timeout: 10_000 }).catch(() => undefined);
  }
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
    data: { name: `e2e-lab-kb-${Date.now()}` },
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

  const prepareRes = await page.request.post(
    productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/prepare-index`),
    { headers: { ...headers, Accept: "application/json" } },
  );
  expect([200, 201], await prepareRes.text()).toContain(prepareRes.status());

  await expect
    .poll(
      async () => {
        const res = await page.request.get(
          productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/readiness`),
          { headers },
        );
        if (!res.ok()) return false;
        const readiness = (await res.json()) as {
          activeSnapshotId?: string | null;
          reindexRequired?: boolean;
        };
        return Boolean(readiness.activeSnapshotId) || readiness.reindexRequired === false;
      },
      { timeout: 180_000, intervals: [1000, 2500, 5000] },
    )
    .toBe(true);

  await page.addInitScript(
    ({ id, kind }) => {
      const key = `lab:evaluation-draft:v1:${kind}`;
      const raw = localStorage.getItem(key);
      const base = raw ? (JSON.parse(raw) as Record<string, unknown>) : { v: 1 };
      base.corpusId = id;
      localStorage.setItem(key, JSON.stringify(base));
    },
    { id: corpusId, kind: draftKind },
  );

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

export type BenchmarkClosurePayload = {
  expectedItems: number;
  executedItems: number;
  failedItems: number;
  skippedItems: number;
  notSupportedItems: number;
  classification: string | null;
};

/** Reads {@code benchmarkClosure} from a terminal lab job status payload. */
export function readBenchmarkClosureFromJobStatus(status: {
  result?: Record<string, unknown> | null;
}): BenchmarkClosurePayload | null {
  const result = status.result;
  if (!result || typeof result !== "object") return null;
  const closure = (result as Record<string, unknown>).benchmarkClosure;
  if (!closure || typeof closure !== "object") return null;
  const c = closure as Record<string, unknown>;
  return {
    expectedItems: typeof c.expectedItems === "number" ? c.expectedItems : 0,
    executedItems: typeof c.executedItems === "number" ? c.executedItems : 0,
    failedItems: typeof c.failedItems === "number" ? c.failedItems : 0,
    skippedItems: typeof c.skippedItems === "number" ? c.skippedItems : 0,
    notSupportedItems: typeof c.notSupportedItems === "number" ? c.notSupportedItems : 0,
    classification: typeof c.classification === "string" ? c.classification : null,
  };
}

export function assertRagBenchmarkClosureAccounting(closure: BenchmarkClosurePayload): void {
  const accounted =
    closure.executedItems +
    closure.failedItems +
    closure.skippedItems +
    closure.notSupportedItems;
  expect(accounted, "executed+failed+skipped+notSupported must equal expectedItems").toBe(
    closure.expectedItems,
  );
  expect(closure.executedItems, "RAG must not finish with zero executed items").toBeGreaterThan(0);
}

export type RagSkipReasonRecord = {
  presetCode?: string;
  outcome: string;
  reason: string;
  questionId?: string;
};

/** Collects SKIPPED / NOT_SUPPORTED rows with human-readable reasons from campaign items export. */
export function collectRagSkipReasonsFromCampaignItems(
  items: Array<Record<string, unknown>>,
): { records: RagSkipReasonRecord[]; notSupported: RagSkipReasonRecord[]; skipped: RagSkipReasonRecord[] } {
  const records: RagSkipReasonRecord[] = [];
  for (const item of items) {
    const mvp = item.mvp as Record<string, unknown> | undefined;
    const operational = mvp?.operational as Record<string, unknown> | undefined;
    const outcome = String(item.outcome ?? operational?.outcome ?? "").trim();
    if (outcome !== "SKIPPED" && outcome !== "NOT_SUPPORTED") continue;
    const reason = String(
      item.skipReason ??
        item.unsupportedReason ??
        operational?.skipReason ??
        operational?.unsupportedReason ??
        operational?.humanReason ??
        item.failureReason ??
        item.reason ??
        item.note ??
        "",
    ).trim();
    const presetFromRow =
      typeof item.presetCode === "string"
        ? item.presetCode
        : typeof operational?.presetCode === "string"
          ? operational.presetCode
          : undefined;
    records.push({
      presetCode: presetFromRow,
      outcome,
      reason: reason.length > 0 ? reason : "(no reason in export row)",
      questionId:
        typeof item.datasetQuestionId === "string"
          ? item.datasetQuestionId
          : typeof item.questionId === "string"
            ? item.questionId
            : undefined,
    });
  }
  return {
    records,
    notSupported: records.filter((r) => r.outcome === "NOT_SUPPORTED"),
    skipped: records.filter((r) => r.outcome === "SKIPPED"),
  };
}

/** Selects the packaged reference workbook dataset when listed in the benchmark dataset select. */
export async function selectReferenceRagDataset(page: Page): Promise<string> {
  const select = page.getByTestId("lab-benchmark-dataset-select");
  await expect(select).toBeVisible({ timeout: 15_000 });
  const options = select.locator("option");
  const count = await options.count();
  for (let i = 0; i < count; i += 1) {
    const opt = options.nth(i);
    const text = ((await opt.textContent()) ?? "").trim();
    const value = (await opt.getAttribute("value")) ?? "";
    if (!value || value === "") continue;
    if (/reference|packaged reference|workbook/i.test(text)) {
      await select.selectOption(value);
      return value;
    }
  }
  const fallback = await select.inputValue();
  expect(fallback.trim().length, "Need a RAG-compatible reference dataset selected").toBeGreaterThan(0);
  return fallback;
}

/** Clicks “select all” and asserts P0–P14 checkboxes are checked when present in catalog. */
export async function selectExperimentalPresetsP0ThroughP14(page: Page): Promise<string[]> {
  await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
  await page.getByTestId("lab-experimental-presets-select-all").click();
  const selected: string[] = [];
  for (let i = 0; i <= 14; i += 1) {
    const code = `P${i}`;
    const box = page.getByTestId(`lab-experimental-preset-${code}`);
    if (await box.isVisible().catch(() => false)) {
      await expect(box).toBeChecked({ timeout: 5_000 });
      selected.push(code);
    }
  }
  expect(
    selected.length,
    "Catalog must expose P0–P14 presets for full RAG preset evidence (missing codes block closure)",
  ).toBeGreaterThanOrEqual(2);
  return selected;
}

/** Injects classpath corpus bootstrap on RAG/embedding benchmark POST (reference workbook actas). */
export function enableRagClasspathCorpusBootstrapOnBenchmarkPost(page: Page): void {
  page.route(/\/lab\/benchmarks\/[^/]+\/runs(?:\?.*)?$/, async (route) => {
    if (route.request().method() !== "POST") {
      await route.continue();
      return;
    }
    const raw = route.request().postData();
    if (!raw) {
      await route.continue();
      return;
    }
    let body: Record<string, unknown>;
    try {
      body = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      await route.continue();
      return;
    }
    body.bootstrapCorpusFromClasspathDocs = true;
    const headers = { ...route.request().headers(), "content-type": "application/json" };
    await route.continue({ postData: JSON.stringify(body), headers });
  });
}

async function attachProjectReadyDocumentsToCorpus(
  page: Page,
  corpusId: string,
  maxDocs = 5,
): Promise<string[]> {
  const headers = await authHeadersFromPage(page);
  const projectsRes = await page.request.get(productApiUrl("/projects?page=0&size=20"), { headers });
  expect(projectsRes.ok(), await projectsRes.text()).toBeTruthy();
  const projectsBody = (await projectsRes.json()) as { items?: Array<{ id: string; name?: string }> };
  const project =
    projectsBody.items?.find((p) => /default/i.test(p.name ?? "")) ?? projectsBody.items?.[0];
  expect(project?.id, "Need a project with READY documents to ground reference workbook questions").toBeTruthy();

  const docsRes = await page.request.get(
    productApiUrl(`/projects/${encodeURIComponent(project!.id)}/documents`),
    { headers },
  );
  expect(docsRes.ok(), await docsRes.text()).toBeTruthy();
  const docs = (await docsRes.json()) as Array<{ id: string; status: string; corpusScope?: string }>;
  const sharedIds = docs
    .filter((d) => d.status === "READY" && (d.corpusScope === "PROJECT_SHARED" || !d.corpusScope))
    .map((d) => d.id)
    .slice(0, maxDocs);
  if (sharedIds.length === 0) {
    return [];
  }

  const attachRes = await page.request.post(
    productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/documents/from-project`),
    {
      headers: { ...headers, "Content-Type": "application/json", Accept: "application/json" },
      data: { projectId: project!.id, documentIds: sharedIds },
    },
  );
  expect(attachRes.ok(), await attachRes.text()).toBeTruthy();
  return sharedIds;
}

/**
 * Fresh evaluation knowledge base: ACTA upload (duplicate guard) plus optional READY project docs
 * so reference-workbook RAG questions can execute against indexed content.
 */
export async function prepareLabRagActaKnowledgeBase(page: Page): Promise<string> {
  const headers = await authHeadersFromPage(page);
  const createRes = await page.request.post(productApiUrl("/lab/evaluation-corpora"), {
    headers: { ...headers, "Content-Type": "application/json", Accept: "application/json" },
    data: { name: `e2e-rag-preset-evidence-${Date.now()}` },
  });
  expect([200, 201], await createRes.text()).toContain(createRes.status());
  const created = (await createRes.json()) as EvaluationCorpusSummary;
  const corpusId = created.id;

  await page.request.delete(productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/documents`), {
    headers,
  });

  const actaPath = actaKnowledgeBaseFilePath();
  const actaBuffer = fs.readFileSync(actaPath);
  const uploadOnce = async () =>
    page.request.post(productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/documents`), {
      headers,
      multipart: {
        files: {
          name: "acta-24-02-2025.txt",
          mimeType: "text/plain",
          buffer: actaBuffer,
        },
      },
    });

  const first = await uploadOnce();
  expect([200, 201], await first.text()).toContain(first.status());
  const second = await uploadOnce();
  expect([200, 201], await second.text()).toContain(second.status());
  const secondBody = (await second.json()) as {
    uploads?: Array<{ status?: string; message?: string }>;
  };
  const duplicateHit = (secondBody.uploads ?? []).some(
    (u) => (u.status ?? "").toUpperCase() === "DUPLICATE" || /duplicate/i.test(u.message ?? ""),
  );
  expect(duplicateHit, "Second ACTA upload must be rejected as duplicate").toBe(true);

  const bootstrapActaPath = ragClasspathBootstrapActaFilePath();
  if (fs.existsSync(bootstrapActaPath)) {
    const bootstrapRes = await page.request.post(
      productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}/documents`),
      {
        headers,
        multipart: {
          files: {
            name: "bootstrap-acta.txt",
            mimeType: "text/plain",
            buffer: fs.readFileSync(bootstrapActaPath),
          },
        },
      },
    );
    expect([200, 201], await bootstrapRes.text()).toContain(bootstrapRes.status());
  }

  await attachProjectReadyDocumentsToCorpus(page, corpusId, 5);

  await expect
    .poll(
      async () => {
        const res = await page.request.get(
          productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}`),
          { headers },
        );
        if (!res.ok()) return null;
        const summary = (await res.json()) as EvaluationCorpusSummary;
        if (summary.documentCount < 1) return null;
        if (summary.readyCount < summary.documentCount) return null;
        return summary;
      },
      { timeout: 300_000, intervals: [1000, 2500, 5000] },
    )
    .not.toBeNull();

  const finalRes = await page.request.get(
    productApiUrl(`/lab/evaluation-corpora/${encodeURIComponent(corpusId)}`),
    { headers },
  );
  expect(finalRes.ok(), await finalRes.text()).toBeTruthy();
  const finalSummary = (await finalRes.json()) as EvaluationCorpusSummary;
  expect(finalSummary.documentCount, "Knowledge base must include ACTA and optional project docs").toBeGreaterThanOrEqual(1);
  expect(finalSummary.readyCount).toBeGreaterThanOrEqual(1);

  await page.evaluate(
    ({ id }) => {
      const key = "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END";
      const raw = localStorage.getItem(key);
      const base = raw ? (JSON.parse(raw) as Record<string, unknown>) : { v: 1 };
      base.corpusId = id;
      localStorage.setItem(key, JSON.stringify(base));
    },
    { id: corpusId },
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

const LAB_JOB_STATUS_TRANSIENT_HTTP = new Set([502, 503, 504]);

export async function fetchLabJobStatus(page: Page, jobId: string): Promise<LabJobStatusBody> {
  const headers = await authHeadersFromPage(page);
  const maxAttempts = 5;
  let lastStatus = 0;
  let lastBody = "";
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const res = await page.request.get(productApiUrl(`/lab/jobs/${jobId}`), { headers });
    lastStatus = res.status();
    lastBody = await res.text();
    if (lastStatus === 200) {
      const raw = JSON.parse(lastBody) as LabJobStatusBody;
      const { campaignId, totalItems } = campaignFieldsFromJobStatus(raw);
      return { ...raw, campaignId, totalItems };
    }
    if (LAB_JOB_STATUS_TRANSIENT_HTTP.has(lastStatus) && attempt + 1 < maxAttempts) {
      await page.waitForTimeout(1500 + attempt * 2000);
      continue;
    }
    break;
  }
  expect(lastStatus, lastBody).toBe(200);
  const raw = JSON.parse(lastBody) as LabJobStatusBody;
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

export async function fetchCampaignComparison(
  page: Page,
  campaignId: string,
): Promise<Record<string, unknown>> {
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(
    productApiUrl(`/lab/campaigns/${encodeURIComponent(campaignId)}/comparison`),
    { headers },
  );
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as Record<string, unknown>;
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

export function indexSnapshotIdsFromCampaignSummary(summary: Record<string, unknown>): string[] {
  const meta = summary.meta as Record<string, unknown> | undefined;
  const raw = meta?.indexSnapshotIds ?? summary.indexSnapshotIds;
  if (!Array.isArray(raw)) return [];
  return raw.filter((id): id is string => typeof id === "string" && id.trim().length > 0);
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
