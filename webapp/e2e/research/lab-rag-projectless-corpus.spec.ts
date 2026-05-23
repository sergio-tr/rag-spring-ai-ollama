import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";
import {
  assertLabDatasetControlsVisible,
  assertNoForbiddenLabCopy,
  clearActiveProjectForLab,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabTerminalOutcome,
} from "../support/lab-helpers";

/**
 * LAB closure: RAG evaluation without active project — evaluation corpus panel + run.
 */
test.describe("LAB RAG projectless corpus @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(240_000);
    await clearActiveProjectForLab(page);
    await loginAsSeedUser(page);
  });

  test("shows evaluation corpus panel and no active-project gate @fullstack @critical", async ({ page }) => {
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);

    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/select an active project before running a rag/i)).toHaveCount(0);
    await expect(page.getByText(/corpus and snapshot preparation are project-scoped/i)).toHaveCount(0);

    await assertNoForbiddenLabCopy(page);
  });

  test("can start RAG run with dataset and presets without project @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "rag");

    test.skip(!(await labDatasetRunnable(page)), "No VALID RAG dataset in environment.");

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("lab-experimental-presets-select-core").click();

    const runButton = page.getByTestId("lab-rag-run");
    await expect(runButton).toBeEnabled({ timeout: 30_000 });

    await runButton.click();

    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Stopped watching here/i)).toHaveCount(0);
    await expect(page.getByText(/Stopped waiting/i)).toHaveCount(0);

    const outcome = await pollLabTerminalOutcome(page, 150_000);
    test.skip(outcome === "job_running", "Job still running after timeout — stack slow or Ollama unavailable.");
    expect(["results", "job_done", "comparison"]).toContain(outcome);
  });
});
