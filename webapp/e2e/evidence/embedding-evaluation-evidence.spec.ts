import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  cancelAllActiveLabJobs,
  collectEmbeddingModelValidation,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  ensureLabEvaluationCorpusReadyViaApi,
  fetchCampaignComparison,
  fetchCampaignSummary,
  gotoLabEvaluationPage,
  indexSnapshotIdsFromCampaignSummary,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  prepareLabAdminE2eTest,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";
import {
  captureEvidence,
  evidenceLog,
  PHASE2_EVIDENCE_ROOT,
  screenshotsOnly,
  selectThesisEmbeddingModels,
  shouldRunCampaigns,
  waitForCompletion,
  writeEvidenceJson,
  sha256File,
} from "../support/evidence-helpers";

test.describe("Embedding evaluation evidence @evidence", () => {
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("embedding screenshots and optional campaign", async ({ page }) => {
    const runCampaign = shouldRunCampaigns();
    test.setTimeout(runCampaign ? 900_000 : 300_000);
    evidenceLog(`START embedding-evidence screenshotsOnly=${screenshotsOnly()} runCampaign=${runCampaign}`);

    await (runCampaign ? prepareLabAdminE2eTest(page) : prepareLabE2eTest(page));

    const modelValidation = await collectEmbeddingModelValidation(page);
    writeEvidenceJson("raw_exports", "embedding-model-validation.json", modelValidation);
    evidenceLog(`model validation status=${modelValidation.status}`);

    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);
    await captureEvidence(page, "embedding", "01_embedding_page_initial.png", { labPage: true });

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);
    await captureEvidence(page, "embedding", "02_embedding_dataset_selected.png", { labPage: true });

    const corpusId = await ensureLabEvaluationCorpusReadyViaApi(page, "EMBEDDING_RETRIEVAL");
    await gotoLabEvaluationPage(page, "embedding");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);
    await expect
      .poll(
        async () => {
          const text = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
          return text.includes(corpusId.slice(0, 8)) || /\(\s*[1-9]\d*\s*(ready|listos)\s*\)/i.test(text);
        },
        { timeout: 60_000, intervals: [500, 1500, 3000] },
      )
      .toBe(true);
    await captureEvidence(page, "embedding", "03_embedding_corpus_ready.png", { labPage: true });

    const modelsGroup = page
      .getByTestId("lab-benchmark-embedding-models-group")
      .or(page.getByRole("group", { name: /Embedding model/i }));
    await expect(modelsGroup.first()).toBeVisible({ timeout: 20_000 });
    await modelsGroup.first().scrollIntoViewIfNeeded();
    await captureEvidence(page, "embedding", "04_embedding_models_panel.png", { labPage: true });

    const embeddingModelIds = await selectThesisEmbeddingModels(page);
    evidenceLog(`thesis embedding models: ${embeddingModelIds.join(", ")}`);
    await captureEvidence(page, "embedding", "05_embedding_models_selected.png", { labPage: true });

    const runBtn = page
      .getByTestId("lab-embedding-run")
      .or(page.getByRole("button", { name: /Run evaluation|Run embedding comparison/i }));
    await expect(runBtn.first()).toBeEnabled({ timeout: 30_000 });

    if (screenshotsOnly() && !runCampaign) {
      await captureEvidence(page, "embedding", "06_embedding_run_clicked.png", { labPage: true });
      evidenceLog("PASS embedding screenshots-only");
      return;
    }

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    await runBtn.first().click();
    await assertLabRunStarted(page);
    await captureEvidence(page, "embedding", "06_embedding_run_clicked.png", { labPage: true });

    const job = await waitForSingleActiveLabJob(page, "EMBEDDING_RETRIEVAL");
    evidenceLog(`active jobId=${job.jobId}`);
    await captureEvidence(page, "embedding", "07_embedding_job_in_progress.png", { labPage: true });

    if (!waitForCompletion() && !runCampaign) {
      evidenceLog("STOP before terminal — EVIDENCE_WAIT_FOR_COMPLETION not set");
      return;
    }

    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000, {
      log: evidenceLog,
      campaignId: acceptedCapture.accepted.campaignId,
    });
    const terminalStatus = (terminal.status ?? "").trim().toUpperCase();
    evidenceLog(`terminal status=${terminalStatus}`);
    expect(terminalStatus).toBe("SUCCEEDED");
    await captureEvidence(page, "embedding", "08_embedding_job_completed.png", { labPage: true });

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId).toBeTruthy();
    evidenceLog(`campaignId=${campaignId}`);

    const summary = await fetchCampaignSummary(page, campaignId!);
    const alignedSnapshotIds = indexSnapshotIdsFromCampaignSummary(summary);
    writeEvidenceJson("raw_exports", "embedding-campaign-summary.json", summary);
    writeEvidenceJson("raw_exports", "embedding-campaign-payload.json", {
      request: acceptedCapture.request,
      accepted: acceptedCapture.accepted,
      embeddingModelIds,
      modelValidation,
      jobId: job.jobId,
      terminalStatus,
      campaignId,
      alignedSnapshotIds,
      corpusId,
    });

    const outcome = await pollLabTerminalOutcome(page, 120_000);
    evidenceLog(`UI outcome=${outcome}`);
    await expect(page.getByTestId("lab-campaign-comparison-panel")).toBeVisible({ timeout: 60_000 });
    await captureEvidence(page, "embedding", "09_embedding_results_summary.png", { labPage: true });

    const resultsTable = page.getByTestId("lab-results-table").or(page.getByTestId("lab-campaign-comparison-panel"));
    if (await resultsTable.first().isVisible().catch(() => false)) {
      await captureEvidence(page, "embedding", "10_embedding_results_table.png", {
        element: resultsTable.first(),
        labPage: true,
      });
    } else {
      await captureEvidence(page, "embedding", "10_embedding_results_table.png", { labPage: true });
    }

    const comparison = await fetchCampaignComparison(page, campaignId!);
    writeEvidenceJson("raw_exports", "embedding-campaign-comparison.json", comparison);

    const itemsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-items.json");
    const itemsCsv = await downloadCampaignExportText(page, campaignId!, "items.csv");
    const rollupsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-summary.json");
    writeEvidenceJson("raw_exports", "embedding-items.json", itemsJson);
    writeEvidenceJson("raw_exports", "embedding-rollups.json", rollupsJson);

    const csvPath = `${PHASE2_EVIDENCE_ROOT}/raw_exports/embedding-items.csv`;
    const fs = await import("node:fs");
    fs.mkdirSync(`${PHASE2_EVIDENCE_ROOT}/raw_exports`, { recursive: true });
    fs.writeFileSync(csvPath, itemsCsv, "utf8");
    writeEvidenceJson("raw_exports", "embedding-export-sha256.json", {
      "embedding-campaign-summary.json": sha256File(`${PHASE2_EVIDENCE_ROOT}/raw_exports/embedding-campaign-summary.json`),
      "embedding-campaign-comparison.json": sha256File(
        `${PHASE2_EVIDENCE_ROOT}/raw_exports/embedding-campaign-comparison.json`,
      ),
      "embedding-items.csv": sha256File(csvPath),
    });

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 30_000 });
    await captureEvidence(page, "embedding", "11_embedding_export_available.png", { labPage: true });
    evidenceLog("PASS embedding campaign");
  });
});
