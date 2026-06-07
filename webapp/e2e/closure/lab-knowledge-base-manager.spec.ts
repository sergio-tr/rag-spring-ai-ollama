import { expect, test } from "@playwright/test";
import { actaKnowledgeBaseFilePath } from "../fixtures/documents";
import {
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
} from "../support/lab-helpers";

test.describe("Closure LAB knowledge base manager @closure @fullstack @wave2", () => {
  test.beforeEach(async ({ page }) => {
    await prepareLabE2eTest(page);
  });

  test("manages documents: upload, duplicate, delete, re-upload, clear all @closure", async ({ page }) => {
    test.setTimeout(240_000);

    await gotoLabEvaluationPage(page, "rag");

    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await assertNoForbiddenLabCopy(page);

    const actaPath = actaKnowledgeBaseFilePath();
    const uploadInput = page.getByTestId("lab-corpus-upload-input");
    await expect(uploadInput).toBeAttached({ timeout: 10_000 });

    await uploadInput.setInputFiles(actaPath);

    const actaName = /acta-24-02-2025\.txt/i;
    await expect
      .poll(
        async () => {
          const list = kbPanel.getByTestId("lab-corpus-document-list");
          if (!(await list.isVisible().catch(() => false))) {
            return false;
          }
          const row = list.locator("li").filter({ hasText: actaName }).first();
          if (!(await row.isVisible().catch(() => false))) {
            return false;
          }
          const status = (await row.textContent()) ?? "";
          return /ready|listo/i.test(status) || /processing|procesando/i.test(status);
        },
        { timeout: 120_000, intervals: [500, 1500, 3000, 5000] },
      )
      .toBe(true);

    await uploadInput.setInputFiles(actaPath);
    await expect(page.getByTestId("lab-kb-duplicate-warning")).toBeVisible({ timeout: 30_000 });
    await expect(kbPanel.getByTestId("lab-corpus-document-list").locator("li")).toHaveCount(1, {
      timeout: 10_000,
    });

    const docRow = kbPanel.getByTestId("lab-corpus-document-list").locator("li").filter({ hasText: actaName }).first();
    const deleteBtn = docRow.getByRole("button", { name: /^(remove|quitar)$/i });
    await deleteBtn.click();

    await expect(kbPanel.getByTestId("lab-corpus-document-list")).toHaveCount(0, { timeout: 20_000 });
    await expect(kbPanel.getByText(actaName)).toHaveCount(0);

    await uploadInput.setInputFiles(actaPath);
    await expect
      .poll(
        async () => {
          const summary = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          return /\(\s*1\s*(ready|listos)\s*\)/i.test(summary) || /1 documentos?\s*\(\s*1\s*listos?\s*\)/i.test(summary);
        },
        { timeout: 120_000, intervals: [500, 1500, 3000, 5000] },
      )
      .toBe(true);

    page.once("dialog", (dialog) => dialog.accept());
    await page.getByTestId("lab-corpus-clear-all").click();

    await expect(kbPanel.getByTestId("lab-corpus-document-list")).toHaveCount(0, { timeout: 20_000 });
    await expect(page.getByTestId("lab-corpus-summary")).toContainText(/0 document/i);
  });
});
