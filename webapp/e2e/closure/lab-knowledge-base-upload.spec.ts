import { expect, test } from "@playwright/test";
import { sampleTextFilePath } from "../fixtures/documents";
import {
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
} from "../support/lab-helpers";

test.describe("Closure LAB knowledge base upload @closure @fullstack @wave2", () => {
  test("uploads document via UI and reaches READY @closure @fullstack", async ({ page }) => {
    test.setTimeout(180_000);

    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "rag");

    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await expect(kbPanel.getByText(/base de conocimiento|knowledge base/i).first()).toBeVisible();
    await expect(kbPanel.getByText(/\bcorpus\b/i)).toHaveCount(0);
    await assertNoForbiddenLabCopy(page);

    const uploadInput = page.getByTestId("lab-corpus-upload-input");
    await expect(uploadInput).toBeAttached({ timeout: 10_000 });
    await uploadInput.setInputFiles(sampleTextFilePath());

    await expect
      .poll(
        async () => {
          const text = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          return /\(\s*[1-9]\d*\s*(ready|listos)\s*\)/i.test(text);
        },
        { timeout: 120_000, intervals: [500, 1500, 3000, 5000] },
      )
      .toBe(true);

    await expect(kbPanel.getByText(/\b(failed|fallido|error)\b/i)).toHaveCount(0);
  });
});
