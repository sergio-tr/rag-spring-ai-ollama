import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  ensureLabEvaluationCorpusReadyViaApi,
  fetchCampaignSummary,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.docs/evidence/wave-2-current/w2-rag/exports",
);

test.describe("Closure LAB RAG knowledge-base evaluation @closure @fullstack @wave2", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("RAG: KB documents READY, presets, single job, progress, results, export", async ({ page }) => {
    test.setTimeout(900_000);

    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);

    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await expect(kbPanel.getByText(/base de conocimiento|knowledge base/i).first()).toBeVisible();
    await expect(kbPanel.getByText(/\bcorpus\b/i)).toHaveCount(0);
    await expect(page.getByText(/select an active project before running a rag/i)).toHaveCount(0);

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    const corpusId = await ensureLabEvaluationCorpusReadyViaApi(page, "RAG_PRESET_END_TO_END");
    await gotoLabEvaluationPage(page, "rag");
    await expect
      .poll(
        async () => {
          const text = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          return /\(\s*[1-9]\d*\s*(ready|listos)\s*\)/i.test(text);
        },
        { timeout: 90_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await page.getByTestId("lab-experimental-presets-select-core").click();

    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "screenshot-config.png"), fullPage: true });

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 30_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    expect(acceptedCapture.request.corpusId, "RAG must send knowledge base id as corpusId").toBe(corpusId);
    expect(acceptedCapture.request.projectId ?? null).toBeNull();
    expect(acceptedCapture.request.datasetId?.trim().length ?? 0).toBeGreaterThan(0);
    expect((acceptedCapture.request.experimentalPresetCodes?.length ?? 0) >= 2).toBe(true);

    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    expect(job.jobId).toBeTruthy();

    await expect(page.getByTestId("lab-job-panel")).toBeVisible({ timeout: 30_000 });
    await expect
      .poll(
        async () => {
          const panel = page.getByTestId("lab-job-panel");
          const t = (await panel.textContent()) ?? "";
          return /running|en curso|progreso|progress|\d+\s*\/\s*\d+/i.test(t);
        },
        { timeout: 120_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "screenshot-progress.png"), fullPage: true });

    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    expect((terminal.status ?? "").toUpperCase(), await page.content()).toBe("SUCCEEDED");

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    if (campaignId) {
      const summary = await fetchCampaignSummary(page, campaignId);
      const meta = summary.meta as Record<string, unknown> | undefined;
      const snapshotIds = meta?.indexSnapshotIds;
      if (Array.isArray(snapshotIds) && snapshotIds.length > 0) {
        expect(snapshotIds.every((id) => typeof id === "string" && id.trim().length > 0)).toBe(true);
      }
    }

    const outcome = await pollLabTerminalOutcome(page, 60_000);
    expect(["results", "comparison", "job_done"]).toContain(outcome);

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("lab-export-primary-json")).toBeVisible({ timeout: 15_000 });
    const advancedExports = page.getByTestId("lab-benchmark-export-advanced");
    await advancedExports.locator("summary").click();
    await expect(page.getByTestId("lab-export-campaign-items-json")).toBeVisible({ timeout: 15_000 });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "screenshot-results.png"), fullPage: true });

    if (campaignId) {
      const itemsJson = await downloadCampaignExportJson(page, campaignId, "campaign-items.json");
      const summaryJson = await downloadCampaignExportJson(page, campaignId, "campaign-summary.json");
      const itemsCsv = await downloadCampaignExportText(page, campaignId, "items.csv");
      const summaryCsv = await downloadCampaignExportText(page, campaignId, "summary.csv");

      expect(itemsJson.exportKind).toBe("campaign-items");
      expect(summaryJson.exportKind).toBe("campaign-summary");
      expect(String(itemsJson.knowledgeBaseId ?? "").length + String(itemsJson.knowledgeBaseName ?? "").length).toBeGreaterThan(0);
      const items = itemsJson.items as Array<Record<string, unknown>> | undefined;
      expect(Array.isArray(items) && items.length > 0).toBe(true);
      const first = items![0];
      expect(first.presetCode ?? first.presetLabel).toBeTruthy();
      expect(first).toHaveProperty("sources");
      expect(first).toHaveProperty("question");

      fs.writeFileSync(path.join(EVIDENCE_DIR, "campaign-items.json"), JSON.stringify(itemsJson, null, 2));
      fs.writeFileSync(path.join(EVIDENCE_DIR, "campaign-summary.json"), JSON.stringify(summaryJson, null, 2));
      fs.writeFileSync(path.join(EVIDENCE_DIR, "campaign-items.csv"), itemsCsv);
      fs.writeFileSync(path.join(EVIDENCE_DIR, "campaign-summary.csv"), summaryCsv);
      fs.writeFileSync(
        path.join(EVIDENCE_DIR, "rag-kb-campaign-payload.json"),
        JSON.stringify(
          { corpusId, request: acceptedCapture.request, accepted: acceptedCapture.accepted },
          null,
          2,
        ),
      );
    }

    await expect(page.getByText(/\b_UNKNOWN\b/i)).toHaveCount(0);

    await assertNoForbiddenLabCopy(page);
  });
});
