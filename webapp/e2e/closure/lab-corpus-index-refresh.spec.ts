import { expect, test } from "@playwright/test";
import { actaKnowledgeBaseFilePath } from "../fixtures/documents";
import {
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
} from "../support/lab-helpers";

test.describe("Closure LAB corpus index refresh @closure @fullstack @wave2", () => {
  test.beforeEach(async ({ page }) => {
    await prepareLabE2eTest(page);
  });

  test("import, prepare index, and run enable without page refresh @closure", async ({ page }) => {
    test.setTimeout(360_000);

    await gotoLabEvaluationPage(page, "rag");
    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await assertNoForbiddenLabCopy(page);

    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeDisabled();

    const actaPath = actaKnowledgeBaseFilePath();
    const actaName = /acta-24-02-2025\.txt/i;
    await page.getByTestId("lab-corpus-upload-input").setInputFiles(actaPath);

    await expect
      .poll(
        async () => {
          const summary = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          const listCount = await kbPanel.getByTestId("lab-corpus-document-list").locator("li").count();
          return summary.match(/[1-9]\d*/) != null && listCount >= 1;
        },
        { timeout: 120_000, intervals: [500, 1500, 3000, 5000] },
      )
      .toBe(true);

    await expect(kbPanel.getByTestId("lab-corpus-document-list").locator("li").filter({ hasText: actaName })).toBeVisible();

    await expect
      .poll(
        async () => {
          const row = kbPanel
            .getByTestId("lab-corpus-document-list")
            .locator("li")
            .filter({ hasText: actaName })
            .first();
          const state = await row.locator("[data-ingestion-state]").getAttribute("data-ingestion-state");
          return state === "READY";
        },
        { timeout: 120_000, intervals: [500, 1500, 3000, 5000] },
      )
      .toBe(true);

    const prepareBtn = page.getByTestId("lab-corpus-prepare-index");
    const prepareVisible = await prepareBtn.isVisible().catch(() => false);
    if (prepareVisible) {
      await prepareBtn.click();
      await expect(page.getByTestId("lab-corpus-prepare-index-progress")).toBeVisible({ timeout: 10_000 });
      await expect
        .poll(
          async () => {
            const success = await page.getByTestId("lab-corpus-success").count();
            const hint = await page.getByTestId("lab-corpus-index-hint").count();
            return success > 0 || hint === 0;
          },
          { timeout: 180_000, intervals: [1000, 2000, 4000] },
        )
        .toBe(true);
    }

    await expect(runBtn).toBeEnabled({ timeout: 60_000 });
    await expect(page.getByTestId("lab-corpus-not-ready-hint")).toHaveCount(0);
    await expect(page.getByText(/REINDEX_REQUIRED|NO_ACTIVE_SNAPSHOT/i)).toHaveCount(0);
  });
});
