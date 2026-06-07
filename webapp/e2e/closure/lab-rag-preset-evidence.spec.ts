import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  assertRagBenchmarkClosureAccounting,
  cancelAllActiveLabJobs,
  collectRagSkipReasonsFromCampaignItems,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  fetchCampaignComparison,
  fetchCampaignSummary,
  fetchLabJobStatus,
  gotoLabEvaluationPage,
  indexSnapshotIdsFromCampaignSummary,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  enableRagClasspathCorpusBootstrapOnBenchmarkPost,
  prepareLabE2eTest,
  prepareLabRagActaKnowledgeBase,
  readBenchmarkClosureFromJobStatus,
  selectExperimentalPresetsP0ThroughP14,
  selectReferenceRagDataset,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.cursor/evidence/final-lab-rag-closure/rag-presets",
);
const LOG_PATH = path.join(EVIDENCE_DIR, "e2e-rag-preset-evidence.log");

function evidenceLog(line: string): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.appendFileSync(LOG_PATH, `${new Date().toISOString()} ${line}\n`, "utf8");
}

function writeJsonEvidence(filename: string, payload: unknown): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.writeFileSync(path.join(EVIDENCE_DIR, filename), `${JSON.stringify(payload, null, 2)}\n`, "utf8");
}

test.describe("Closure LAB RAG preset P0–P14 evidence @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("RAG P0–P14: ACTA knowledge base, closure accounting, exports", async ({ page }, testInfo) => {
    test.setTimeout(3_600_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(LOG_PATH, "", "utf8");
    evidenceLog("START lab-rag-preset-evidence");

    await prepareLabE2eTest(page);
    enableRagClasspathCorpusBootstrapOnBenchmarkPost(page);
    const corpusId = await prepareLabRagActaKnowledgeBase(page);
    evidenceLog(`corpusId=${corpusId} acta READY (+ project docs when available for workbook grounding)`);

    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);
    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 15_000 });

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 90_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    const datasetId = await selectReferenceRagDataset(page);
    evidenceLog(`datasetId=${datasetId}`);

    const selectedPresets = await selectExperimentalPresetsP0ThroughP14(page);
    evidenceLog(`selectedPresets=${selectedPresets.join(",")} count=${selectedPresets.length}`);

    const expectedSummary = (await page.getByTestId("lab-expected-items-summary").textContent()) ?? "";
    const expectedMatch = expectedSummary.match(/=\s*(\d+)\s*(items|ítems)/i);
    const uiExpectedItems = expectedMatch ? Number(expectedMatch[1]) : 0;
    expect(uiExpectedItems, `expected items summary: ${expectedSummary}`).toBeGreaterThan(0);
    evidenceLog(`uiExpectedItems=${uiExpectedItems} summary=${expectedSummary.trim()}`);

    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-config.png"), fullPage: true });

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 30_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    expect(acceptedCapture.request.corpusId).toBe(corpusId);
    expect(acceptedCapture.request.datasetId?.trim().length ?? 0).toBeGreaterThan(0);
    expect(acceptedCapture.request.experimentalPresetCodes?.length ?? 0).toBe(selectedPresets.length);

    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    evidenceLog(`jobId=${job.jobId}`);

    const jobPanel = page.getByTestId("lab-job-panel");
    await expect(jobPanel).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("lab-progress-summary")).toBeVisible({ timeout: 120_000 });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-progress.png"), fullPage: true });

    const subtasks = page.getByTestId("lab-subtask-list");
    await expect
      .poll(async () => (await subtasks.isVisible().catch(() => false)) && (await subtasks.locator("li").count()) >= 1, {
        timeout: 180_000,
        intervals: [1000, 2000, 4000],
      })
      .toBe(true);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-subtasks.png"), fullPage: true });

    const terminal = await pollLabJobTerminal(page, job.jobId!, 3_300_000);
    const terminalStatus = (terminal.status ?? "").trim().toUpperCase();
    evidenceLog(`terminal status=${terminalStatus}`);
    expect(terminalStatus, "RAG preset campaign must not finish as empty SUCCEEDED").toBe("SUCCEEDED");

    const jobStatus = await fetchLabJobStatus(page, job.jobId!);
    const closure = readBenchmarkClosureFromJobStatus(jobStatus);
    expect(closure, "terminal job must include benchmarkClosure").not.toBeNull();
    assertRagBenchmarkClosureAccounting(closure!);
    evidenceLog(
      `closure expected=${closure!.expectedItems} executed=${closure!.executedItems} skipped=${closure!.skippedItems} notSupported=${closure!.notSupportedItems}`,
    );

    if (uiExpectedItems > 0) {
      expect(closure!.expectedItems).toBe(uiExpectedItems);
    }
    expect(
      closure!.notSupportedItems + closure!.skippedItems + closure!.executedItems + closure!.failedItems,
    ).toBe(closure!.expectedItems);

    await expect(jobPanel.getByTestId("lab-benchmark-closure-summary")).toBeVisible();
    await expect(jobPanel.getByTestId("lab-empty-success-warning")).toHaveCount(0);

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId).toBeTruthy();

    const summary = await fetchCampaignSummary(page, campaignId!);
    const alignedSnapshotIds = indexSnapshotIdsFromCampaignSummary(summary);
    if (alignedSnapshotIds.length > 0) {
      evidenceLog(`indexSnapshotIds=${alignedSnapshotIds.join(", ")}`);
    }

    writeJsonEvidence("rag-campaign-summary.json", summary);

    const outcome = await pollLabTerminalOutcome(page, 120_000);
    evidenceLog(`UI outcome=${outcome}`);
    expect(["comparison", "results", "job_done"]).toContain(outcome);

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 60_000 });

    const comparison = await fetchCampaignComparison(page, campaignId!);
    const comparisonRows = Array.isArray(comparison.rows) ? comparison.rows : [];
    expect(comparisonRows.length).toBeGreaterThanOrEqual(2);
    evidenceLog(`comparison preset rows=${comparisonRows.length}`);

    const itemsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-items.json");
    const itemsCsv = await downloadCampaignExportText(page, campaignId!, "items.csv");
    const rollupsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-summary.json");

    const itemRecords = Array.isArray(itemsJson.items) ? itemsJson.items : [];
    expect(itemRecords.length).toBeGreaterThan(0);
    expect(itemsCsv.trim().length).toBeGreaterThan(0);

    const skipReasons = collectRagSkipReasonsFromCampaignItems(itemRecords);
    for (const row of skipReasons.skipped) {
      expect(row.reason.trim().length, `skipped row missing reason: ${JSON.stringify(row)}`).toBeGreaterThan(2);
    }
    for (const row of skipReasons.notSupported) {
      expect(row.reason.trim().length, `NOT_SUPPORTED row missing reason: ${JSON.stringify(row)}`).toBeGreaterThan(2);
    }
    writeJsonEvidence("rag-skip-reasons.json", skipReasons);
    writeJsonEvidence("rag-items.json", itemsJson);
    writeJsonEvidence("rag-rollups.json", rollupsJson);
    fs.writeFileSync(path.join(EVIDENCE_DIR, "rag-items.csv"), itemsCsv, "utf8");

    writeJsonEvidence("rag-campaign-payload.json", {
      corpusId,
      datasetId,
      selectedPresets,
      uiExpectedItems,
      request: acceptedCapture.request,
      accepted: acceptedCapture.accepted,
      benchmarkClosure: closure,
      jobId: job.jobId,
      campaignId,
      alignedSnapshotIds,
    });

    await assertNoForbiddenLabCopy(page);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-results.png"), fullPage: true });

    const traceSrc = path.join(testInfo.outputDir, "trace.zip");
    if (fs.existsSync(traceSrc)) {
      fs.copyFileSync(traceSrc, path.join(EVIDENCE_DIR, "trace.zip"));
      evidenceLog("trace.zip copied");
    }

    evidenceLog("PASS lab-rag-preset-evidence");
  });
});
