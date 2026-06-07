import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";
import {
  assertNoForbiddenLabCopy,
  clearActiveProjectForLab,
  collectVisibleMainText,
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
      if (path.includes("/embedding")) {
        await expect(page.getByTestId("lab-embedding-eval-page")).toBeVisible({ timeout: 12_000 });
        await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 12_000 });
      } else if (path.includes("/llm")) {
        await expect(page.getByTestId("lab-llm-eval-page")).toBeVisible({ timeout: 12_000 });
      } else if (path.includes("/rag")) {
        await expect(page.getByTestId("lab-rag-eval-page")).toBeVisible({ timeout: 12_000 });
      }
      await assertNoForbiddenLabCopy(page);
    });
  }

  test("empty trend block hidden until results exist @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    await expect(page.getByTestId("lab-benchmark-trend-graph")).toHaveCount(0);
    await expect(page.getByText(/Trend toward optimal answers/i)).toHaveCount(0);
  });

  test("RAG page explains unsupported presets with friendly copy @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "rag");
    await expect(page.getByTestId("lab-rag-eval-page")).toBeVisible({ timeout: 12_000 });
    const mainText = await collectVisibleMainText(page);
    expect(mainText).not.toMatch(/\bRAG_PRESET_END_TO_END\b/);
    expect(mainText).not.toMatch(/\bFUTURE_MULTI_TURN_NOT_SELECTABLE\b/);
    expect(mainText).not.toMatch(/\bREQUIRES_MULTI_TURN\b/);
    const blocked = page.locator('[data-testid^="lab-preset-blocked-"]').first();
    if (await blocked.isVisible().catch(() => false)) {
      await expect(blocked).toHaveText(/not available for this evaluation type|no está disponible para este tipo/i);
    }
  });

  test("saved RAG draft with P13/P14 is sanitized on open @fullstack", async ({ page }) => {
    await gotoLabEvaluationPage(page, "rag");
    await expect(page.getByTestId("lab-rag-eval-page")).toBeVisible({ timeout: 12_000 });
    await page.evaluate(() => {
      localStorage.setItem(
        "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END",
        JSON.stringify({
          v: 1,
          datasetId: null,
          selectedExperimentalPresetCodes: ["P0", "P13", "P14"],
          corpusId: null,
        }),
      );
    });
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("lab-rag-eval-page")).toBeVisible({ timeout: 12_000 });
    await expect(page.getByTestId("lab-draft-presets-sanitized")).toHaveText(
      /not available for this evaluation type and were removed|no están disponibles para este tipo de evaluación/i,
      { timeout: 10_000 },
    );
    const mainText = await collectVisibleMainText(page);
    expect(mainText).not.toMatch(/\bFUTURE_MULTI_TURN_NOT_SELECTABLE\b/);
    await expect(page.getByTestId("lab-experimental-preset-P0")).toBeChecked();
    await expect(page.getByTestId("lab-experimental-preset-P13")).not.toBeChecked();
  });
});
