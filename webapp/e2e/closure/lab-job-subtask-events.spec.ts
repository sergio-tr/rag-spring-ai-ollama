import { expect, test } from "@playwright/test";
import {
  assertNoForbiddenLabCopy,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
} from "../support/lab-helpers";

test.describe("Closure LAB job subtask events @closure @fullstack @wave2", () => {
  test.beforeEach(async ({ page }) => {
    await prepareLabE2eTest(page);
  });

  test("shows structured phases and item counter without technical spam @closure", async ({ page }) => {
    test.setTimeout(300_000);

    await gotoLabEvaluationPage(page, "rag");
    await assertNoForbiddenLabCopy(page);

    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 120_000 });

    await runBtn.click();

    const panel = page.getByTestId("lab-job-panel");
    await expect(panel).toBeVisible({ timeout: 60_000 });

    await expect(page.getByTestId("lab-progress-summary")).toBeVisible({ timeout: 90_000 });

    const spamResolving = panel.getByText(/Resolving typed dataset/i);
    await expect(spamResolving).toHaveCount(0);

    const technical = page.getByTestId("lab-technical-events");
    await expect(technical).toBeVisible();

    const subtasks = page.getByTestId("lab-subtask-list");
    await expect
      .poll(
        async () => {
          if (!(await subtasks.isVisible().catch(() => false))) {
            return false;
          }
          return (await subtasks.locator("li").count()) >= 1;
        },
        { timeout: 120_000, intervals: [1000, 2000, 4000] },
      )
      .toBe(true);

    await expect
      .poll(
        async () => {
          const counter = page.getByTestId("lab-job-item-counter");
          if (!(await counter.isVisible().catch(() => false))) {
            return false;
          }
          const text = (await counter.textContent()) ?? "";
          return /\d+\s*\/\s*\d+/.test(text);
        },
        { timeout: 180_000, intervals: [1000, 2000, 4000, 8000] },
      )
      .toBe(true);

    await technical.click();
    await expect(technical).toHaveAttribute("open", "");
  });
});
