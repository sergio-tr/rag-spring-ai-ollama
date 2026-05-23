import { expect, type Page } from "@playwright/test";
import { authHeadersFromPage, productApiUrl } from "./helpers";

/** Clears persisted active project so LAB runs are projectless. */
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

export async function labDatasetRunnable(page: Page): Promise<boolean> {
  const needsDataset = await page.getByTestId("lab-benchmark-needs-dataset-warn").isVisible().catch(() => false);
  if (needsDataset) return false;
  const value = await page.getByTestId("lab-benchmark-dataset-select").inputValue().catch(() => "");
  return value.trim().length > 0;
}

export const FORBIDDEN_LAB_UI_PATTERNS: RegExp[] = [
  /POST JSON/i,
  /canonical benchmark API/i,
  /Stopped watching here/i,
  /Stopped waiting/i,
  /datasetId.*projectId.*llmModelId/i,
  /SSE:\s*\/lab/i,
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

export async function selectLlmModelsForComparison(page: Page, count: number): Promise<boolean> {
  const multi = page.getByTestId("lab-benchmark-llm-models-multi");
  if (!(await multi.isVisible().catch(() => false))) return false;
  const options = multi.locator("option");
  const n = await options.count();
  if (n < count) return false;
  const values: string[] = [];
  for (let i = 0; i < n && values.length < count; i += 1) {
    const v = await options.nth(i).getAttribute("value");
    if (v && v.trim()) values.push(v);
  }
  if (values.length < Math.min(count, 2)) return false;
  await multi.selectOption(values.slice(0, count));
  return true;
}

export async function selectEmbeddingModelsForComparison(page: Page, count: number): Promise<boolean> {
  const multi = page.getByTestId("lab-benchmark-embedding-models-multi");
  if (!(await multi.isVisible().catch(() => false))) return false;
  const options = multi.locator("option");
  const n = await options.count();
  if (n < count) return false;
  const values: string[] = [];
  for (let i = 0; i < n && values.length < count; i += 1) {
    const v = await options.nth(i).getAttribute("value");
    if (v && v.trim()) values.push(v);
  }
  if (values.length < Math.min(count, 2)) return false;
  await multi.selectOption(values.slice(0, count));
  return true;
}

export async function fetchActiveLabJobs(page: Page): Promise<unknown[]> {
  const res = await page.request.get(productApiUrl("/lab/jobs/active"), {
    headers: await authHeadersFromPage(page),
  });
  if (!res.ok()) return [];
  const body = await res.json();
  return Array.isArray(body) ? body : [];
}
