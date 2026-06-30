import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  cancelAllActiveLabJobs,
  ensureLabEvaluationCorpusReadyViaApi,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  prepareLabE2eTest,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.docs/evidence/final-lab-rag-closure/rag-no-empty-success",
);

test.describe("Closure RAG no empty success @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("valid RAG run reports executed items and closure summary", async ({ page }) => {
    test.setTimeout(900_000);

    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    await ensureLabEvaluationCorpusReadyViaApi(page, "RAG_PRESET_END_TO_END");
    await gotoLabEvaluationPage(page, "rag");

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await page.getByTestId("lab-experimental-presets-select-core").click();

    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 30_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    const status = (terminal.status ?? "").trim().toUpperCase();
    expect(status, "RAG benchmark must not finish as empty SUCCEEDED").toBe("SUCCEEDED");

    const jobPanel = page.getByTestId("lab-job-panel");
    await expect(jobPanel).toBeVisible({ timeout: 15_000 });
    await expect(jobPanel.getByTestId("lab-benchmark-closure-summary")).toBeVisible();
    const closureText = (await jobPanel.getByTestId("lab-benchmark-closure-summary").textContent()) ?? "";
    const executedMatch = closureText.match(/(\d+)\s+executed/i);
    expect(executedMatch, `closure line must show executed count: ${closureText}`).not.toBeNull();
    expect(Number(executedMatch![1]), closureText).toBeGreaterThan(0);

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 60_000 });
    const executedChip = page.getByTestId("lab-outcome-EXECUTED");
    await expect(executedChip).toBeVisible();
    const chipText = (await executedChip.textContent()) ?? "";
    const chipCount = chipText.match(/(\d+)/);
    expect(chipCount, chipText).not.toBeNull();
    expect(Number(chipCount![1])).toBeGreaterThan(0);

    await expect(jobPanel.getByTestId("lab-empty-success-warning")).toHaveCount(0);
    await expect(jobPanel.getByRole("status")).not.toContainText(/No items executed/i);

    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-closure-success.png"), fullPage: true });
  });

  test("skipped rows in results table show a reason in the note column", async ({ page }) => {
    test.setTimeout(900_000);

    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);
    await ensureLabEvaluationCorpusReadyViaApi(page, "RAG_PRESET_END_TO_END");
    await gotoLabEvaluationPage(page, "rag");
    await page.getByTestId("lab-experimental-presets-select-core").click();
    await page.getByTestId("lab-rag-run").click();
    await assertLabRunStarted(page);
    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    await pollLabJobTerminal(page, job.jobId!, 720_000);
    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 120_000 });

    const skippedRows = page.locator('[data-testid^="lab-results-row-"]').filter({
      has: page.locator("td", { hasText: /Skipped|Omitida/i }),
    });
    const skippedCount = await skippedRows.count();
    if (skippedCount === 0) {
      test.skip(true, "No skipped rows in this run — reason column check not applicable");
    }
    const firstNote = await skippedRows.first().locator("td").last().textContent();
    expect((firstNote ?? "").trim().length, "Skipped item must expose a reason").toBeGreaterThan(2);
  });
});
