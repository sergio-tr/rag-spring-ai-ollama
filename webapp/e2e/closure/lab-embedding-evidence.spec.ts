import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  collectEmbeddingModelValidation,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  ensureEmbeddingCampaignModelsReady,
  ensureLabEvaluationCorpusReadyViaApi,
  fetchCampaignComparison,
  fetchCampaignSummary,
  gotoLabEvaluationPage,
  indexSnapshotIdsFromCampaignSummary,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectEmbeddingModelsByIds,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";
import { authHeadersFromPage, productApiUrl } from "../support/helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../docs/evidence/final-lab-rag-closure/embeddings",
);
const LOG_PATH = path.join(EVIDENCE_DIR, "e2e-embedding-evidence.log");

function evidenceLog(line: string): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.appendFileSync(LOG_PATH, `${new Date().toISOString()} ${line}\n`, "utf8");
}

function writeJsonEvidence(filename: string, payload: unknown): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.writeFileSync(path.join(EVIDENCE_DIR, filename), `${JSON.stringify(payload, null, 2)}\n`, "utf8");
}

test.describe("Closure LAB embedding model evidence @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("embedding comparison campaign: aligned snapshots, retrieval metrics, exports", async ({
    page,
  }, testInfo) => {
    test.setTimeout(900_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(LOG_PATH, "", "utf8");
    evidenceLog("START lab-embedding-evidence");

    await prepareLabE2eTest(page);

    const modelValidation = await collectEmbeddingModelValidation(page);
    evidenceLog(`model validation status=${modelValidation.status}`);
    writeJsonEvidence("embedding-model-validation.json", modelValidation);

    if (modelValidation.status === "BLOCKED_BY_MODEL_AVAILABILITY") {
      evidenceLog(
        `BLOCKED_BY_MODEL_AVAILABILITY compatible=[${modelValidation.selectableCompatibleEmbeddingIds.join(", ")}]`,
      );
    }

    const embeddingModelIds = await ensureEmbeddingCampaignModelsReady(page, 2);
    evidenceLog(`compatible embeddings for run: ${embeddingModelIds.join(", ")}`);
    expect(embeddingModelIds.length).toBeGreaterThanOrEqual(2);

    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    const corpusId = await ensureLabEvaluationCorpusReadyViaApi(page, "EMBEDDING_RETRIEVAL");
    await gotoLabEvaluationPage(page, "embedding");
    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 15_000 });
    await expect
      .poll(
        async () => {
          const text = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          return text.includes(corpusId.slice(0, 8)) || /\(\s*[1-9]\d*\s*(ready|listos)\s*\)/i.test(text);
        },
        { timeout: 60_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);

    await selectEmbeddingModelsByIds(page, embeddingModelIds);
    await expect(page.getByTestId("lab-comparison-selection-hint")).toContainText(
      /Comparing|comparando|embeddings/i,
    );

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByRole("button", {
      name: /Run embedding comparison|comparación de embeddings/i,
    });
    await expect(runBtn).toBeEnabled({ timeout: 30_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    await expect
      .poll(() => Boolean(acceptedCapture.accepted.campaignId), { timeout: 15_000 })
      .toBe(true);

    const reqEmb = acceptedCapture.request.embeddingModelIds ?? [];
    expect(reqEmb.length).toBeGreaterThanOrEqual(2);
    const reqSnaps = acceptedCapture.request.indexSnapshotIds;
    if (reqSnaps != null && reqSnaps.length > 0) {
      expect(reqSnaps.length).toBe(reqEmb.length);
      expect(reqSnaps.every((id) => typeof id === "string" && id.trim().length > 0)).toBe(true);
      evidenceLog(`client sent indexSnapshotIds count=${reqSnaps.length}`);
    } else {
      evidenceLog("indexSnapshotIds omitted - backend auto-aligns snapshots");
    }

    const job = await waitForSingleActiveLabJob(page, "EMBEDDING_RETRIEVAL");
    evidenceLog(`active jobId=${job.jobId}`);
    expect(job.jobId).toBeTruthy();

    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    const terminalStatus = (terminal.status ?? "").trim().toUpperCase();
    evidenceLog(`terminal status=${terminalStatus}`);
    expect(terminalStatus).toBe("SUCCEEDED");

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId).toBeTruthy();
    evidenceLog(`campaignId=${campaignId}`);

    const summary = await fetchCampaignSummary(page, campaignId!);
    const alignedSnapshotIds = indexSnapshotIdsFromCampaignSummary(summary);
    expect(alignedSnapshotIds.length).toBe(reqEmb.length);
    expect(alignedSnapshotIds.every((id) => id.trim().length > 0)).toBe(true);
    evidenceLog(`aligned indexSnapshotIds=${alignedSnapshotIds.join(", ")}`);

    writeJsonEvidence("embedding-campaign-payload.json", {
      request: acceptedCapture.request,
      accepted: acceptedCapture.accepted,
      embeddingModelIds,
      modelValidation,
      jobId: job.jobId,
      terminalStatus,
      campaignId,
      alignedSnapshotIds,
    });
    writeJsonEvidence("embedding-campaign-summary.json", summary);

    const outcome = await pollLabTerminalOutcome(page, 120_000);
    evidenceLog(`UI outcome=${outcome}`);
    expect(["comparison", "results", "job_done"]).toContain(outcome);

    await expect(page.getByTestId("lab-campaign-comparison-panel")).toBeVisible({ timeout: 60_000 });
    const comparisonRows = page.getByTestId(/lab-comparison-row-/);
    await expect(comparisonRows).toHaveCount(embeddingModelIds.length, { timeout: 15_000 });

    const comparison = await fetchCampaignComparison(page, campaignId!);
    const rows = Array.isArray(comparison.rows) ? comparison.rows : [];
    expect(rows.length).toBeGreaterThanOrEqual(2);
    const hasRetrievalMetric = rows.some(
      (r) =>
        typeof r === "object" &&
        r != null &&
        (typeof (r as Record<string, unknown>).meanMrr === "number" ||
          typeof (r as Record<string, unknown>).meanRecallAt1 === "number" ||
          typeof (r as Record<string, unknown>).meanExactMatch === "number"),
    );
    expect(hasRetrievalMetric, JSON.stringify(rows)).toBe(true);
    evidenceLog(`comparison rows=${rows.length} with retrieval metrics`);

    const itemsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-items.json");
    const itemsCsv = await downloadCampaignExportText(page, campaignId!, "items.csv");
    const rollupsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-summary.json");

    expect(itemsCsv.trim().length).toBeGreaterThan(0);
    const itemRecords = Array.isArray(itemsJson.items) ? itemsJson.items : [];
    expect(itemRecords.length, JSON.stringify(Object.keys(itemsJson))).toBeGreaterThan(0);
    const exportHasRecall = JSON.stringify(itemsJson).match(/recallAt|mrr|retrievedCount/i);
    expect(exportHasRecall, "items export should include retrieval metrics").not.toBeNull();

    writeJsonEvidence("embedding-items.json", itemsJson);
    writeJsonEvidence("embedding-rollups.json", rollupsJson);
    fs.writeFileSync(path.join(EVIDENCE_DIR, "embedding-items.csv"), itemsCsv, "utf8");

    const headers = await authHeadersFromPage(page);
    const runsRes = await page.request.get(productApiUrl(`/lab/campaigns/${campaignId}/runs`), {
      headers,
    });
    expect(runsRes.ok(), await runsRes.text()).toBeTruthy();
    const campaignRuns = (await runsRes.json()) as Array<Record<string, unknown>>;
    expect(campaignRuns.length).toBeGreaterThanOrEqual(2);

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 30_000 });
    const executedChip = page.getByTestId("lab-outcome-EXECUTED");
    if (await executedChip.isVisible().catch(() => false)) {
      const chipText = (await executedChip.textContent()) ?? "";
      const chipCount = chipText.match(/(\d+)/);
      if (chipCount) {
        expect(Number(chipCount[1])).toBeGreaterThan(0);
      }
    }

    await assertNoForbiddenLabCopy(page);
    await expect(page.getByText(/EMBEDDING_CAMPAIGN_MISSING_INDEX_SNAPSHOT/i)).toHaveCount(0);
    await expect(page.getByText(/EMBEDDING_CAMPAIGN_REQUIRES_ALIGNED_INDEX_SNAPSHOT_IDS/i)).toHaveCount(0);

    await page.screenshot({ path: path.join(EVIDENCE_DIR, "embedding-results.png"), fullPage: true });
    evidenceLog("screenshot embedding-results.png");

    const traceSrc = path.join(testInfo.outputDir, "trace.zip");
    if (fs.existsSync(traceSrc)) {
      fs.copyFileSync(traceSrc, path.join(EVIDENCE_DIR, "trace.zip"));
      evidenceLog("trace.zip copied to evidence");
    } else {
      evidenceLog(`trace.zip not found at ${traceSrc}`);
    }

    evidenceLog("PASS lab-embedding-evidence");
  });
});
