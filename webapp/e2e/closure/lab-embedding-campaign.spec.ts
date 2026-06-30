import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  downloadCampaignExportJson,
  ensureEmbeddingCampaignModelsReady,
  ensureLabEvaluationCorpusReadyViaApi,
  fetchCampaignSummary,
  indexSnapshotIdsFromCampaignSummary,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectEmbeddingModelsByIds,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

test.describe("Closure LAB embedding campaign @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("embedding campaign: one job, aligned snapshots, export JSON @closure @fullstack", async ({
    page,
  }) => {
    test.setTimeout(600_000);

    await prepareLabE2eTest(page);
    const modelIds = await ensureEmbeddingCampaignModelsReady(page, 2);
    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    const corpusId = await ensureLabEvaluationCorpusReadyViaApi(page, "EMBEDDING_RETRIEVAL");
    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);
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

    await selectEmbeddingModelsByIds(page, modelIds);

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

    const evidenceDir = path.resolve(__dirname, "../../../docs/evidence/wave-1-current/p0-04");
    fs.mkdirSync(evidenceDir, { recursive: true });
    fs.writeFileSync(
      path.join(evidenceDir, "embedding-campaign-payload.json"),
      JSON.stringify(
        { request: acceptedCapture.request, accepted: acceptedCapture.accepted },
        null,
        2,
      ),
    );

    const reqEmb = acceptedCapture.request.embeddingModelIds ?? [];
    expect(reqEmb.length).toBeGreaterThanOrEqual(2);
    expect(reqEmb.every((id) => typeof id === "string" && id.trim().length > 0)).toBe(true);
    expect(reqEmb.some((id) => id == null)).toBe(false);

    const reqSnaps = acceptedCapture.request.indexSnapshotIds;
    if (reqSnaps != null && reqSnaps.length > 0) {
      expect(reqSnaps.length).toBe(reqEmb.length);
      expect(reqSnaps.every((id) => typeof id === "string" && id.trim().length > 0)).toBe(true);
    }

    const job = await waitForSingleActiveLabJob(page, "EMBEDDING_RETRIEVAL");
    expect(job.jobId).toBeTruthy();

    const terminal = await pollLabJobTerminal(page, job.jobId!, 540_000);
    expect((terminal.status ?? "").toUpperCase()).toBe("SUCCEEDED");

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId).toBeTruthy();

    const totalItems = acceptedCapture.accepted.totalItems ?? terminal.totalItems;
    if (totalItems != null && totalItems > 0) {
      expect(totalItems).toBeGreaterThan(0);
    }

    const summary = await fetchCampaignSummary(page, campaignId!);
    const alignedSnapshotIds = indexSnapshotIdsFromCampaignSummary(summary);
    expect(
      alignedSnapshotIds.length,
      "Campaign meta must record indexSnapshotIds aligned with embedding models",
    ).toBe(reqEmb.length);
    expect(alignedSnapshotIds.every((id) => id.trim().length > 0)).toBe(true);

    const outcome = await pollLabTerminalOutcome(page, 60_000);
    expect(["comparison", "results", "job_done"]).toContain(outcome);
    await expect(page.getByTestId("lab-campaign-comparison-panel")).toBeVisible({ timeout: 30_000 });

    const exportJson = await downloadCampaignExportJson(page, campaignId!);
    expect(exportJson).toBeTruthy();
    expect(JSON.stringify(exportJson).length).toBeGreaterThan(10);

    await page.screenshot({
      path: path.join(evidenceDir, "embedding-campaign-results.png"),
      fullPage: true,
    });

    await assertNoForbiddenLabCopy(page);
    await expect(page.getByText(/EMBEDDING_CAMPAIGN_MISSING_INDEX_SNAPSHOT/i)).toHaveCount(0);
    await expect(page.getByText(/EMBEDDING_CAMPAIGN_REQUIRES_ALIGNED_INDEX_SNAPSHOT_IDS/i)).toHaveCount(0);
  });
});
