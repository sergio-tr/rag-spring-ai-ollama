import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertRagBenchmarkClosureAccounting,
  cancelAllActiveLabJobs,
  collectRagSkipReasonsFromCampaignItems,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  ensureLabEvaluationCorpusReadyViaApi,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  prepareLabE2eTest,
  readBenchmarkClosureFromJobStatus,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../docs/evidence/p0-lab-rag-runtime-closure/rag-no-skips",
);

function appendEvidenceLog(testInfo: import("@playwright/test").TestInfo, line: string): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  const logPath = path.join(EVIDENCE_DIR, "e2e-rag-no-silent-skips.log");
  const stamp = new Date().toISOString();
  fs.appendFileSync(logPath, `[${stamp}] [${testInfo.title}] ${line}\n`);
}

test.describe("Closure RAG no silent skips @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("P0/P2 run executes items, honest closure, exports with skip reasons", async ({ page }, testInfo) => {
    test.setTimeout(900_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });

    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    await ensureLabEvaluationCorpusReadyViaApi(page, "RAG_PRESET_END_TO_END");
    await gotoLabEvaluationPage(page, "rag");

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await page.getByTestId("lab-experimental-presets-clear").click();
    await page.getByTestId("lab-experimental-preset-P0").check();
    await page.getByTestId("lab-experimental-preset-P2").check();

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 30_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    const status = (terminal.status ?? "").trim().toUpperCase();
    appendEvidenceLog(testInfo, `terminal status=${status}`);
    expect(status, "valid RAG run must not finish as empty SUCCEEDED").toBe("SUCCEEDED");

    const closure = readBenchmarkClosureFromJobStatus(terminal);
    expect(closure, "terminal job must include benchmarkClosure").not.toBeNull();
    assertRagBenchmarkClosureAccounting(closure!);
    appendEvidenceLog(
      testInfo,
      `closure classification=${closure!.classification} executed=${closure!.executedItems} expected=${closure!.expectedItems}`,
    );

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId, "RAG campaign id required for exports").toBeTruthy();

    const jobPanel = page.getByTestId("lab-job-panel");
    await expect(jobPanel).toBeVisible({ timeout: 15_000 });
    await expect(jobPanel.getByTestId("lab-benchmark-closure-summary")).toBeVisible();
    await expect(jobPanel.getByTestId("lab-empty-success-warning")).toHaveCount(0);
    await expect(page.getByTestId("lab-benchmark-no-executed-warning")).toHaveCount(0);

    const closureText = (await jobPanel.getByTestId("lab-benchmark-closure-summary").textContent()) ?? "";
    expect(closureText).toMatch(/executed|ejecutad/i);
    expect(closureText).toMatch(/skipped|omitid/i);
    expect(closureText).toMatch(/not supported|no soportad/i);

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 60_000 });
    const executedChip = page.getByTestId("lab-outcome-EXECUTED");
    await expect(executedChip).toBeVisible();
    const chipText = (await executedChip.textContent()) ?? "";
    const chipCount = chipText.match(/(\d+)/);
    expect(chipCount, chipText).not.toBeNull();
    expect(Number(chipCount![1])).toBeGreaterThan(0);

    const exportJson = await downloadCampaignExportJson(page, campaignId!, "campaign-items.json");
    const exportCsv = await downloadCampaignExportText(page, campaignId!, "items.csv");
    const items = (exportJson.items ?? []) as Array<Record<string, unknown>>;
    expect(items.length).toBeGreaterThan(0);
    expect(exportCsv.length).toBeGreaterThan(20);

    const { notSupported, skipped, records } = collectRagSkipReasonsFromCampaignItems(items);
    for (const row of records) {
      expect(row.reason.trim().length, `skip/notSupported row must have reason: ${JSON.stringify(row)}`).toBeGreaterThan(
        2,
      );
      expect(row.reason).not.toMatch(/^\(no reason in export row\)$/);
    }
    if (notSupported.length > 0) {
      appendEvidenceLog(testInfo, `notSupported presets: ${notSupported.map((r) => r.presetCode).join(",")}`);
      for (const row of notSupported) {
        expect(row.presetCode?.length ?? 0).toBeGreaterThan(0);
      }
    }
    if (skipped.length > 0) {
      appendEvidenceLog(testInfo, `skipped count=${skipped.length}`);
    }

    fs.writeFileSync(path.join(EVIDENCE_DIR, "rag-outcomes.json"), JSON.stringify(closure, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "rag-export.json"), JSON.stringify(exportJson, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "rag-export.csv"), exportCsv);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-outcome-summary.png"), fullPage: true });
    appendEvidenceLog(testInfo, "evidence artifacts written");
  });
});
