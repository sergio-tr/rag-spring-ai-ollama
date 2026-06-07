import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectEmbeddingModelsForComparison,
} from "../support/lab-helpers";

test.describe("LAB embedding model comparison @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(300_000);
    await prepareLabE2eTest(page);
  });

  test("multi-select embeddings and run comparison or clear errors @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);
    test.skip(!(await labDatasetRunnable(page)), "No VALID embedding dataset.");

    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 10_000 });

    const selected = await selectEmbeddingModelsForComparison(page, 2);
    test.skip(!selected, "Fewer than two embedding models in catalog.");

    const runBtn = page.getByRole("button", { name: /Run embedding comparison|comparación de embeddings/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await runBtn.click();

    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });

    const outcome = await pollLabTerminalOutcome(page, 180_000);
    test.skip(outcome === "job_running", "Embedding campaign still running.");

    if (outcome === "error") {
      const alert = page.locator('[data-slot="card"]').getByRole("alert").first();
      const msg = (await alert.textContent()) ?? "";
      expect(msg.length).toBeGreaterThan(0);
      expect(msg).not.toMatch(/POST JSON|canonical benchmark/i);
      return;
    }

    expect(["results", "comparison", "job_done"]).toContain(outcome);
  });
});
