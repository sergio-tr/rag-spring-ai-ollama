import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertNoForbiddenLabCopy,
  ensureLabEvaluationCorpusReadyViaApi,
  gotoLabEvaluationPage,
  prepareLabE2eTest,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.docs/evidence/p0-lab-rag-runtime-closure/human-errors",
);

const FORBIDDEN_IN_MAIN = [
  /\bBLOCKED_BY_MODEL_AVAILABILITY\b/,
  /\bEMBEDDING_DIMENSION_MISMATCH\b/,
  /\bNO_READY_DOCUMENTS\b/,
  /\bFAILED_STALE_INGESTION\b/,
  /\bMODEL_UNAVAILABLE\b/,
  /\bDATASET_INVALID\b/,
];

function assertNoTechnicalConstantsInMain(mainText: string): void {
  for (const re of FORBIDDEN_IN_MAIN) {
    expect(mainText, `Technical constant visible in main UI: ${re}`).not.toMatch(re);
  }
}

test.describe("Closure human error messages @closure @fullstack @wave2", () => {
  test.beforeEach(async ({ page }) => {
    await prepareLabE2eTest(page);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  });

  test("RAG empty knowledge base shows human hint, not NO_READY_DOCUMENTS @closure", async ({ page }) => {
    test.setTimeout(120_000);

    await gotoLabEvaluationPage(page, "rag");
    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 15_000 });

    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeDisabled({ timeout: 30_000 });

    const notReadyHint = page.getByTestId("lab-corpus-not-ready-hint");
    await expect(notReadyHint).toBeVisible();
    const hintText = (await notReadyHint.textContent()) ?? "";
    expect(hintText.length).toBeGreaterThan(10);
    expect(hintText).not.toMatch(/\bNO_READY_DOCUMENTS\b/);

    const mainText = (await page.locator("main").innerText()) ?? "";
    assertNoTechnicalConstantsInMain(mainText);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "human-error-message.png"), fullPage: true });
    await assertNoForbiddenLabCopy(page);
  });

  test("benchmark API error maps to human message; code only in technical details @closure", async ({
    page,
  }) => {
    test.setTimeout(120_000);

    await ensureLabEvaluationCorpusReadyViaApi(page, "RAG_PRESET_END_TO_END");
    await gotoLabEvaluationPage(page, "rag");
    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await expect
      .poll(
        async () => {
          const text = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          return /\(\s*[1-9]\d*\s*(ready|listos)\s*\)/i.test(text);
        },
        { timeout: 120_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);

    await page.route("**/api/v5/lab/benchmarks/RAG_PRESET_END_TO_END/runs", async (route) => {
      if (route.request().method() !== "POST") {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({ message: "NO_READY_DOCUMENTS" }),
      });
    });

    await page.getByTestId("lab-experimental-presets-select-core").click();
    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 60_000 });
    await runBtn.click();

    const userError = page.getByTestId("lab-eval-user-error");
    await expect(userError).toBeVisible({ timeout: 30_000 });
    const userText = (await userError.textContent()) ?? "";
    expect(userText).not.toMatch(/\bNO_READY_DOCUMENTS\b/);
    expect(userText).toMatch(/knowledge base|base de conocimiento|documentos listos|ready/i);

    const techDetails = page.getByTestId("lab-eval-technical-details");
    await techDetails.locator("summary").click();
    const codeEl = page.getByTestId("lab-eval-technical-error-code");
    await expect(codeEl).toBeVisible();
    await expect(codeEl).toContainText("NO_READY_DOCUMENTS");

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "technical-details-code.png"),
      fullPage: true,
    });

  });

  test("LLM model availability banner is human-readable when shown @closure", async ({ page }) => {
    test.setTimeout(90_000);

    await gotoLabEvaluationPage(page, "llm");
    const banner = page.getByTestId("lab-llm-model-availability-blocked");
    const visible = await banner.isVisible().catch(() => false);
    if (!visible) {
      test.skip(true, "Environment has two or more LLM models — availability banner not shown");
    }
    const text = (await banner.textContent()) ?? "";
    expect(text).not.toMatch(/\bBLOCKED_BY_MODEL_AVAILABILITY\b/);
    expect(text.length).toBeGreaterThan(20);
    assertNoTechnicalConstantsInMain((await page.locator("main").innerText()) ?? "");
  });
});
