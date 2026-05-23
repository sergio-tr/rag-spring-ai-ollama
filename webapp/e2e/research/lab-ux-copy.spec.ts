import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";
import {
  assertNoForbiddenLabCopy,
  clearActiveProjectForLab,
  gotoLabEvaluationPage,
} from "../support/lab-helpers";

const LAB_PATHS = ["/en/lab/evaluation/llm", "/en/lab/evaluation/embedding", "/en/lab/evaluation/rag"] as const;

test.describe("LAB UX copy guards @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    await clearActiveProjectForLab(page);
    await loginAsSeedUser(page);
  });

  for (const path of LAB_PATHS) {
    test(`no technical API copy on ${path} @fullstack @critical`, async ({ page }) => {
      await page.goto(path, { waitUntil: "domcontentloaded", timeout: 15_000 });
      await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
        timeout: 12_000,
      });
      await assertNoForbiddenLabCopy(page);
    });
  }

  test("empty trend block hidden until results exist @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    await expect(page.getByTestId("lab-benchmark-trend-graph")).toHaveCount(0);
    await expect(page.getByText(/Trend toward optimal answers/i)).toHaveCount(0);
  });
});
