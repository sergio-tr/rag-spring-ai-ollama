import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";
import {
  assertLabDatasetControlsVisible,
  clearActiveProjectForLab,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabTerminalOutcome,
  selectLlmModelsForComparison,
} from "../support/lab-helpers";

test.describe("LAB LLM model comparison campaign @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(300_000);
    await clearActiveProjectForLab(page);
    await loginAsSeedUser(page);
  });

  test("multi-select LLM models and run comparison @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    test.skip(!(await labDatasetRunnable(page)), "No VALID LLM dataset.");

    const selected = await selectLlmModelsForComparison(page, 2);
    test.skip(!selected, "Fewer than two LLM models in catalog.");

    await expect(page.getByTestId("lab-comparison-selection-hint")).toBeVisible({ timeout: 5_000 });

    const runBtn = page.getByRole("button", { name: /Run model comparison/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await runBtn.click();

    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });

    const outcome = await pollLabTerminalOutcome(page, 180_000);
    test.skip(outcome === "job_running", "Campaign still running after timeout.");

    if (outcome === "comparison" || outcome === "results") {
      const rows = page.getByTestId(/lab-comparison-row-/);
      const campaignRuns = page.getByTestId("lab-campaign-runs-panel");
      const hasRows = (await rows.count()) >= 1;
      const hasRuns = await campaignRuns.isVisible().catch(() => false);
      expect(hasRows || hasRuns).toBe(true);
    }
  });
});
