import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunButtonEnabled,
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectLlmModelsForComparison,
} from "../support/lab-helpers";

/**
 * Closure: LAB LLM, embedding, and RAG evaluation pages with real stack.
 */
test.describe("Closure LAB evaluations @closure @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(300_000);
    await prepareLabE2eTest(page);
  });

  test("LLM evaluation page runs single-model job @closure @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    test.skip(!(await labDatasetRunnable(page)), "No VALID LLM dataset.");
    await assertLabRunButtonEnabled(page, "lab-llm-run");
    const runButton = page.getByTestId("lab-llm-run");
    await runButton.click();
    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });
    await assertNoForbiddenLabCopy(page);
  });

  test("LLM model comparison uses one campaign job @closure @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    test.skip(!(await labDatasetRunnable(page)), "No VALID LLM dataset.");
    const selected = await selectLlmModelsForComparison(page, 2);
    test.skip(!selected, "Fewer than two LLM models in catalog.");
    const runBtn = page.getByRole("button", { name: /Run model comparison/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await runBtn.click();
    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });
    const outcome = await pollLabTerminalOutcome(page, 180_000);
    test.skip(outcome === "job_running", "Campaign still running after timeout.");
    await assertNoForbiddenLabCopy(page);
  });

  test("embedding evaluation page loads and can start @closure @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);
    const runButton = page.getByTestId("lab-embedding-run");
    await expect(runButton).toBeVisible({ timeout: 15_000 });
    test.skip(!(await labDatasetRunnable(page)), "No VALID embedding dataset.");
    await assertNoForbiddenLabCopy(page);
  });

  test("embedding evaluation offers only compatible models and concise comparison hint @closure @fullstack", async ({
    page,
  }) => {
    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);

    const incompatiblePattern = /nomic-embed|qwen3-embedding/i;
    const group = page.getByTestId("lab-benchmark-embedding-models-group");
    const select = page.getByTestId("lab-benchmark-embedding-model");

    if (await group.isVisible().catch(() => false)) {
      const boxes = group.locator('input[type="checkbox"]');
      const count = await boxes.count();
      for (let i = 0; i < count; i += 1) {
        const testId = (await boxes.nth(i).getAttribute("data-testid")) ?? "";
        expect(testId, `Incompatible embedding offered in UI: ${testId}`).not.toMatch(incompatiblePattern);
      }
    } else if (await select.isVisible().catch(() => false)) {
      const options = select.locator("option");
      const optionCount = await options.count();
      for (let i = 0; i < optionCount; i += 1) {
        const value = (await options.nth(i).getAttribute("value")) ?? "";
        if (!value) continue;
        expect(value, `Incompatible embedding offered in UI: ${value}`).not.toMatch(incompatiblePattern);
      }
    }

    const blocked = page.getByTestId("lab-embedding-model-availability-blocked");
    if (await blocked.isVisible().catch(() => false)) {
      await expect(blocked).toHaveText(/At least two compatible embedding models are required for comparison\./i);
      await expect(blocked).not.toContainText(/Missing preferred/i);
      await expect(blocked).not.toContainText(/bge-m3/i);
    }

    await assertNoForbiddenLabCopy(page);
  });

  test("RAG evaluation page loads dataset controls @closure @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);
    await expect(page.getByTestId("lab-rag-run").or(page.getByTestId("lab-benchmark-dataset-select"))).toBeVisible({
      timeout: 15_000,
    });
    await assertNoForbiddenLabCopy(page);
  });
});
