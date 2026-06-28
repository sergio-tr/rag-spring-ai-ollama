import { expect, test } from "@playwright/test";
import { actaKnowledgeBaseFilePath } from "../fixtures/documents";
import {
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
  uploadLabCorpusFileViaUi,
} from "../support/lab-helpers";

test.describe("Closure LAB document-centric RAG @closure @fullstack @wave2", () => {
  test.beforeEach(async ({ page }) => {
    await prepareLabE2eTest(page);
  });

  test("upload, run without manual index or project steps @closure", async ({ page }) => {
    test.setTimeout(360_000);

    await gotoLabEvaluationPage(page, "rag");
    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await assertNoForbiddenLabCopy(page);

    await expect(page.getByTestId("lab-corpus-import-hint")).toHaveCount(0);
    await expect(page.getByTestId("lab-corpus-attach-project")).toHaveCount(0);
    await expect(page.getByTestId("lab-corpus-prepare-index")).toHaveCount(0);
    await expect(page.getByText(/No active project selected/i)).toHaveCount(0);

    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeDisabled();

    const actaPath = actaKnowledgeBaseFilePath();
    const actaName = /acta-24-02-2025\.txt/i;
    await uploadLabCorpusFileViaUi(page, actaPath);

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

    await expect(page.getByTestId("lab-corpus-index-will-prepare")).toBeVisible({ timeout: 15_000 });
    await expect(runBtn).toBeEnabled({ timeout: 60_000 });
    await expect(page.getByTestId("lab-corpus-not-ready-hint")).toHaveCount(0);
    await expect(page.getByText(/REINDEX_REQUIRED|NO_ACTIVE_SNAPSHOT|INDEX_PREPARATION_REQUIRED/i)).toHaveCount(
      0,
    );

    await page.getByTestId("lab-experimental-presets-select-core").click();
    await runBtn.click();

    await expect(page.getByTestId("lab-eval-preparation-progress")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Preparing documents and indexes|Preparando documentos e índices/i)).toBeVisible();
    await assertNoForbiddenLabCopy(page);
  });
});
