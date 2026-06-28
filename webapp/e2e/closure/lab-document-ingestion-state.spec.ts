import { expect, test } from "@playwright/test";
import { actaKnowledgeBaseFilePath } from "../fixtures/documents";
import {
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
  uploadLabCorpusFileViaUi,
} from "../support/lab-helpers";

test.describe("Closure LAB document ingestion state @closure @fullstack @wave2", () => {
  test.beforeEach(async ({ page }) => {
    await prepareLabE2eTest(page);
  });

  test("upload shows document, reaches READY, enables evaluate without refresh @closure", async ({
    page,
  }) => {
    test.setTimeout(240_000);

    await gotoLabEvaluationPage(page, "rag");
    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await assertNoForbiddenLabCopy(page);

    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeDisabled();

    const actaPath = actaKnowledgeBaseFilePath();
    const actaName = /acta-24-02-2025\.txt/i;
    await uploadLabCorpusFileViaUi(page, actaPath);

    const docRow = kbPanel
      .getByTestId("lab-corpus-document-list")
      .locator("li")
      .filter({ hasText: actaName })
      .first();
    await expect(docRow).toBeVisible({ timeout: 30_000 });

    const docStatus = docRow.locator("[data-ingestion-state]");
    await expect
      .poll(
        async () => {
          const state = await docStatus.getAttribute("data-ingestion-state");
          const text = (await docStatus.textContent()) ?? "";
          return (
            state === "READY" ||
            state === "INGESTING" ||
            /ready|listo|processing|procesando/i.test(text)
          );
        },
        { timeout: 120_000, intervals: [500, 1500, 3000, 5000] },
      )
      .toBe(true);

    await expect
      .poll(async () => (await docStatus.getAttribute("data-ingestion-state")) === "READY", {
        timeout: 120_000,
        intervals: [500, 1500, 3000, 5000],
      })
      .toBe(true);

    await expect(docStatus).toContainText(/ready|listo/i);
    await expect(runBtn).toBeEnabled({ timeout: 15_000 });
    await expect(page.getByTestId("lab-corpus-not-ready-hint")).toHaveCount(0);
  });

  test("invalid empty upload stays failed and keeps evaluate disabled @closure", async ({ page }) => {
    test.setTimeout(120_000);

    await gotoLabEvaluationPage(page, "rag");
    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });

    const uploadInput = page.getByTestId("lab-corpus-upload-input");
    await uploadInput.setInputFiles({
      name: "empty.bin",
      mimeType: "application/octet-stream",
      buffer: Buffer.alloc(0),
    });

    await expect
      .poll(
        async () => {
          const failed = kbPanel.locator('[data-ingestion-state="ERROR"]');
          return (await failed.count()) > 0 || (await page.getByTestId("lab-kb-error").count()) > 0;
        },
        { timeout: 60_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);

    await expect(page.getByTestId("lab-rag-run")).toBeDisabled();
  });
});
