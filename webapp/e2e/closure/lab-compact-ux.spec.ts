import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";
import {
  assertNoForbiddenLabCopy,
  clearActiveProjectForLab,
  gotoLabEvaluationPage,
} from "../support/lab-helpers";

const FORBIDDEN_VISIBLE = [
  /Follow the steps below/i,
  /How to read P0/i,
  /POST JSON/i,
  /\bcorpus\b/i,
  /Guided steps/i,
];

test.describe("Closure LAB compact UX @closure @fullstack", () => {
  test.beforeEach(async ({ page }) => {
    await clearActiveProjectForLab(page);
    await loginAsSeedUser(page);
  });

  test("overview shows four workflow cards without long primer copy @closure", async ({ page }) => {
    await page.goto("/en/lab", { waitUntil: "domcontentloaded", timeout: 15_000 });
    await expect(page.getByTestId("lab-overview-workflow-cards")).toBeVisible({ timeout: 12_000 });
    await expect(page.getByTestId("lab-workflow-card-llm")).toBeVisible();
    await expect(page.getByTestId("lab-workflow-card-embedding")).toBeVisible();
    await expect(page.getByTestId("lab-workflow-card-rag")).toBeVisible();
    await expect(page.getByTestId("lab-workflow-card-classifier")).toBeVisible();
    const mainText = (await page.locator("main").innerText()) ?? "";
    for (const re of FORBIDDEN_VISIBLE) {
      expect(mainText, `Forbidden copy: ${re}`).not.toMatch(re);
    }
    await assertNoForbiddenLabCopy(page);
  });

  test("RAG page hides preset guide and long copy by default @closure", async ({ page }) => {
    await gotoLabEvaluationPage(page, "rag");
    await expect(page.getByTestId("lab-rag-eval-page")).toBeVisible({ timeout: 12_000 });
    await expect(page.getByTestId("lab-rag-preset-help")).toBeVisible();
    const helpOpen = await page.getByTestId("lab-rag-preset-help").evaluate((el) => (el as HTMLDetailsElement).open);
    expect(helpOpen).toBe(false);
    const mainText = (await page.locator("main").innerText()) ?? "";
    expect(mainText).not.toMatch(/How to read P0/i);
    await expect(page.getByTestId("lab-eval-run-card")).toBeVisible();
    await assertNoForbiddenLabCopy(page);
  });

  test("LLM evaluation keeps help collapsed and primary run controls visible @closure", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    await expect(page.getByTestId("lab-llm-eval-page")).toBeVisible({ timeout: 12_000 });
    const helpOpen = await page.getByTestId("lab-eval-guided-help").evaluate((el) => (el as HTMLDetailsElement).open);
    expect(helpOpen).toBe(false);
    await expect(page.getByTestId("lab-llm-run")).toBeVisible();
    const mainText = (await page.locator("main").innerText()) ?? "";
    for (const re of FORBIDDEN_VISIBLE) {
      expect(mainText, `Forbidden copy: ${re}`).not.toMatch(re);
    }
  });
});
