import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  downloadCampaignExportJson,
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

const EVIDENCE_DIR = path.resolve(__dirname, "../../../docs/evidence/wave-2-current/w2-rag");

test.describe("Closure LAB RAG evaluation @closure @fullstack @wave2", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("RAG: knowledge base, auto index, results and export @closure @fullstack", async ({ page }) => {
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
          return text.includes(corpusId.slice(0, 8)) || /\(\s*[1-9]\d*\s*(ready|listos)\s*\)/i.test(text);
        },
        { timeout: 90_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await page.getByTestId("lab-experimental-presets-select-core").click();

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 30_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    expect(acceptedCapture.request.corpusId, "RAG run must send knowledge base (corpusId)").toBeTruthy();
    expect(acceptedCapture.request.datasetId?.trim().length ?? 0).toBeGreaterThan(0);

    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    expect(job.jobId).toBeTruthy();

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
    const advanced = page.getByTestId("lab-benchmark-export-advanced");
    await advanced.locator("summary").click();
    await expect(page.getByTestId("lab-export-campaign-items-json")).toBeVisible({ timeout: 15_000 });

    const comparisonPanel = page.getByTestId("lab-campaign-comparison-panel");
    if (await comparisonPanel.isVisible().catch(() => false)) {
      await expect(comparisonPanel.getByText(/gemma3:4b/i)).toHaveCount(0);
      await expect(comparisonPanel.getByText(/RAG_PRESET_END_TO_END|PRESET_CODE/i)).toHaveCount(0);
      const comparisonRows = page.locator("[data-testid^='lab-comparison-row-']");
      const rowCount = await comparisonRows.count();
      if (rowCount > 0) {
        const firstRowText = (await comparisonRows.first().textContent()) ?? "";
        expect(firstRowText.trim().length).toBeGreaterThan(0);
        expect(firstRowText).not.toMatch(/gemma3:4b/i);
      }
    }

    await expect(page.getByTestId("lab-benchmark-no-executed-warning")).toHaveCount(0);

    const executedBadge = page.getByTestId("lab-outcome-EXECUTED");
    const executedText = (await executedBadge.textContent().catch(() => "")) ?? "";
    if (/\b[1-9]\d*\b/.test(executedText)) {
      const itemRows = page.locator("[data-testid^='lab-item-row-']");
      await expect(itemRows.first()).toBeVisible({ timeout: 15_000 });
    }

    const technicalDetails = page.locator("details[data-testid^='lab-item-technical-']");
    if ((await technicalDetails.count()) > 0) {
      await expect(technicalDetails.first()).not.toHaveAttribute("open", "");
    }

    if (campaignId) {
      fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
      const exportJson = await downloadCampaignExportJson(page, campaignId);
      fs.writeFileSync(
        path.join(EVIDENCE_DIR, "rag-campaign-export.json"),
        JSON.stringify(exportJson, null, 2),
      );
      fs.writeFileSync(
        path.join(EVIDENCE_DIR, "rag-campaign-payload.json"),
        JSON.stringify(
          { request: acceptedCapture.request, accepted: acceptedCapture.accepted },
          null,
          2,
        ),
      );
    }

    await assertNoForbiddenLabCopy(page);
  });
});
