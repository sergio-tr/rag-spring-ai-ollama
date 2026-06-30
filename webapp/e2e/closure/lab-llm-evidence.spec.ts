import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  collectLlmModelValidation,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  ensureLlmCampaignModelsReady,
  fetchCampaignComparison,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectLlmModelsByIds,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";
import { authHeadersFromPage, productApiUrl } from "../support/helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../docs/evidence/final-lab-rag-closure/llm",
);
const LOG_PATH = path.join(EVIDENCE_DIR, "e2e-llm-evidence.log");

function evidenceLog(line: string): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.appendFileSync(LOG_PATH, `${new Date().toISOString()} ${line}\n`, "utf8");
}

function writeJsonEvidence(filename: string, payload: unknown): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.writeFileSync(path.join(EVIDENCE_DIR, filename), `${JSON.stringify(payload, null, 2)}\n`, "utf8");
}

test.describe("Closure LAB LLM model evidence @closure @fullstack", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("LLM comparison campaign produces per-model results and exports", async ({ page }, testInfo) => {
    test.setTimeout(900_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(LOG_PATH, "", "utf8");
    evidenceLog("START lab-llm-evidence");

    await prepareLabE2eTest(page);

    const modelValidation = await collectLlmModelValidation(page);
    evidenceLog(`model validation status=${modelValidation.status}`);
    writeJsonEvidence("llm-model-validation.json", modelValidation);

    if (modelValidation.status === "BLOCKED_BY_MODEL_AVAILABILITY") {
      evidenceLog(
        `BLOCKED_BY_MODEL_AVAILABILITY missingPreferred=[${modelValidation.missingPreferred.join(", ")}]`,
      );
    }

    const llmModelIds = await ensureLlmCampaignModelsReady(page, 3);
    evidenceLog(`selectable LLM models for run: ${llmModelIds.join(", ")}`);
    expect(llmModelIds.length).toBeGreaterThanOrEqual(3);

    await gotoLabEvaluationPage(page, "llm");
    await assertLabDatasetControlsVisible(page);
    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    await selectLlmModelsByIds(page, llmModelIds);
    await expect(page.getByTestId("lab-comparison-selection-hint")).toContainText(/Comparing|comparando/i);

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByRole("button", { name: /Run model comparison/i });
    await expect(runBtn).toBeEnabled({ timeout: 20_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    const job = await waitForSingleActiveLabJob(page, "LLM_JUDGE_QA");
    evidenceLog(`active jobId=${job.jobId}`);
    expect(job.jobId).toBeTruthy();

    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    const terminalStatus = (terminal.status ?? "").trim().toUpperCase();
    evidenceLog(`terminal status=${terminalStatus}`);
    expect(terminalStatus, "LLM campaign must finish successfully").toBe("SUCCEEDED");

    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId).toBeTruthy();
    evidenceLog(`campaignId=${campaignId}`);

    writeJsonEvidence("llm-campaign-payload.json", {
      request: acceptedCapture.request,
      accepted: acceptedCapture.accepted,
      llmModelIds,
      modelValidation,
      jobId: job.jobId,
      terminalStatus,
      campaignId,
    });

    expect(acceptedCapture.request.llmModelIds?.length ?? 0).toBeGreaterThanOrEqual(2);

    const outcome = await pollLabTerminalOutcome(page, 120_000);
    evidenceLog(`UI outcome=${outcome}`);
    expect(["comparison", "results", "job_done"]).toContain(outcome);

    await expect(page.getByTestId("lab-campaign-comparison-panel")).toBeVisible({ timeout: 60_000 });
    const comparisonRows = page.getByTestId(/lab-comparison-row-/);
    await expect(comparisonRows).toHaveCount(llmModelIds.length, { timeout: 15_000 });

    const comparison = await fetchCampaignComparison(page, campaignId!);
    const rows = Array.isArray(comparison.rows) ? comparison.rows : [];
    expect(rows.length).toBeGreaterThanOrEqual(2);
    evidenceLog(`comparison rows=${rows.length}`);

    const itemsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-items.json");
    const itemsCsv = await downloadCampaignExportText(page, campaignId!, "items.csv");
    const rollupsJson = await downloadCampaignExportJson(page, campaignId!, "campaign-summary.json");

    expect(itemsCsv.trim().length).toBeGreaterThan(0);
    const itemRecords = Array.isArray(itemsJson.items) ? itemsJson.items : [];
    expect(itemRecords.length, JSON.stringify(Object.keys(itemsJson))).toBeGreaterThan(0);

    writeJsonEvidence("llm-items.json", itemsJson);
    writeJsonEvidence("llm-rollups.json", rollupsJson);
    fs.writeFileSync(path.join(EVIDENCE_DIR, "llm-items.csv"), itemsCsv, "utf8");

    const headers = await authHeadersFromPage(page);
    const runsRes = await page.request.get(productApiUrl(`/lab/campaigns/${campaignId}/runs`), {
      headers,
    });
    expect(runsRes.ok(), await runsRes.text()).toBeTruthy();
    const campaignRuns = (await runsRes.json()) as Array<Record<string, unknown>>;
    expect(campaignRuns.length).toBeGreaterThanOrEqual(2);
    evidenceLog(`campaign child runs=${campaignRuns.length}`);

    await assertNoForbiddenLabCopy(page);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "llm-results.png"), fullPage: true });
    evidenceLog("screenshot llm-results.png");

    const traceSrc = path.join(testInfo.outputDir, "trace.zip");
    if (fs.existsSync(traceSrc)) {
      fs.copyFileSync(traceSrc, path.join(EVIDENCE_DIR, "trace.zip"));
      evidenceLog("trace.zip copied to evidence");
    } else {
      evidenceLog(`trace.zip not found at ${traceSrc}`);
    }

    evidenceLog("PASS lab-llm-evidence");
  });
});
