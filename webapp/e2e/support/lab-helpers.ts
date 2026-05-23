import { expect, type Page } from "@playwright/test";
import { authHeadersFromPage, productApiUrl } from "./helpers";

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
          (key.startsWith("lab:evaluation-draft:v1:") ||
            key.startsWith("rag-lab-form-v1:") ||
            key.startsWith("rag-lab-"))
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

/** Checks the first N models in the embedding checkbox group (comparison runs). */
export async function selectEmbeddingModelsForComparison(page: Page, count: number): Promise<boolean> {
  const group = page.getByTestId("lab-benchmark-embedding-models-group");
  if (!(await group.isVisible().catch(() => false))) return false;
  const boxes = group.locator('input[type="checkbox"]:not(:disabled)');
  const n = await boxes.count();
  if (n < count) return false;
  for (let i = 0; i < count; i += 1) {
    await boxes.nth(i).check();
  }
  return true;
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
  await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });
}

export async function fetchActiveLabJobs(page: Page): Promise<unknown[]> {
  const res = await page.request.get(productApiUrl("/lab/jobs/active"), {
    headers: await authHeadersFromPage(page),
  });
  if (!res.ok()) return [];
  const body = await res.json();
  return Array.isArray(body) ? body : [];
}
