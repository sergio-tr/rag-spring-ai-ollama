import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test } from "@playwright/test";
import { actaKnowledgeBaseFilePath } from "../fixtures/documents";
import {
  assertLabDatasetControlsVisible,
  assertLabRunStarted,
  assertNoForbiddenLabCopy,
  cancelAllActiveLabJobs,
  downloadCampaignExportJson,
  downloadCampaignExportText,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabJobTerminal,
  prepareLabE2eTest,
  trackBenchmarkCampaignAccepted,
  waitForSingleActiveLabJob,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.docs/evidence/p0-lab-rag-runtime-closure/rag-upload",
);

function actaVariants(): { name: string; mimeType: string; buffer: Buffer }[] {
  const base = fs.readFileSync(actaKnowledgeBaseFilePath());
  return [1, 2, 3].map((n) => ({
    name: `acta-${n}.txt`,
    mimeType: "text/plain",
    buffer: Buffer.concat([base, Buffer.from(`\n# acta-variant-${n}\n`)]),
  }));
}

async function waitForCorpusReadyCount(
  page: import("@playwright/test").Page,
  ready: number,
  timeoutMs = 180_000,
): Promise<void> {
  await expect
    .poll(
      async () => {
        const text = (await page.getByTestId("lab-corpus-summary").textContent()) ?? "";
        const m = text.match(/\(\s*(\d+)\s*(ready|listos)\s*\)/i);
        return m ? Number(m[1]) : 0;
      },
      { timeout: timeoutMs, intervals: [500, 1500, 3000, 5000] },
    )
    .toBeGreaterThanOrEqual(ready);
}

test.describe("Closure RAG evaluation document upload (real) @closure @fullstack @wave2", () => {
  test.describe.configure({ mode: "serial" });

  test.afterEach(async ({ page }) => {
    await cancelAllActiveLabJobs(page);
  });

  test("KB upload, delete, duplicate guard, RAG P0/P2 run with exports @closure", async ({ page }) => {
    test.setTimeout(900_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });

    await prepareLabE2eTest(page);
    await gotoLabEvaluationPage(page, "rag");
    await assertLabDatasetControlsVisible(page);

    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });
    await assertNoForbiddenLabCopy(page);
    await expect(kbPanel.getByText(/\bcorpus\b/i)).toHaveCount(0);

    await page.screenshot({ path: path.join(EVIDENCE_DIR, "kb-empty.png"), fullPage: true });

    const uploadInput = page.getByTestId("lab-corpus-upload-input");
    await expect(uploadInput).toBeAttached({ timeout: 10_000 });

    const variants = actaVariants();
    await uploadInput.setInputFiles(variants);
    await waitForCorpusReadyCount(page, 3);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "upload-ready.png"), fullPage: true });

    const acta2Row = kbPanel
      .getByTestId("lab-corpus-document-list")
      .locator("li")
      .filter({ hasText: /acta-2\.txt/i })
      .first();
    await acta2Row.getByRole("button", { name: /^(remove|quitar)$/i }).click();
    await expect(kbPanel.getByTestId("lab-corpus-document-list").locator("li")).toHaveCount(2, {
      timeout: 20_000,
    });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "delete-without-refresh.png"), fullPage: true });

    await uploadInput.setInputFiles(variants.filter((f) => f.name === "acta-2.txt"));
    await waitForCorpusReadyCount(page, 3);

    await uploadInput.setInputFiles(variants.filter((f) => f.name === "acta-1.txt"));
    await expect(page.getByTestId("lab-kb-duplicate-warning")).toBeVisible({ timeout: 30_000 });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "duplicate-warning.png"), fullPage: true });
    await expect(kbPanel.getByTestId("lab-corpus-document-list").locator("li")).toHaveCount(3, {
      timeout: 10_000,
    });

    await expect
      .poll(() => labDatasetRunnable(page), { timeout: 60_000, intervals: [500, 1500, 3000] })
      .toBe(true);

    await expect(page.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
    await page.getByTestId("lab-experimental-presets-clear").click();
    await page.getByTestId("lab-experimental-preset-P0").check();
    await page.getByTestId("lab-experimental-preset-P2").check();

    const acceptedCapture = trackBenchmarkCampaignAccepted(page);
    const runBtn = page.getByTestId("lab-rag-run");
    await expect(runBtn).toBeEnabled({ timeout: 60_000 });
    await runBtn.click();
    await assertLabRunStarted(page);

    const job = await waitForSingleActiveLabJob(page, "RAG_PRESET_END_TO_END");
    const terminal = await pollLabJobTerminal(page, job.jobId!, 720_000);
    expect((terminal.status ?? "").toUpperCase()).toBe("SUCCEEDED");
    const campaignId = acceptedCapture.accepted.campaignId ?? terminal.campaignId;
    expect(campaignId, "RAG campaign id required for exports").toBeTruthy();

    const jobPanel = page.getByTestId("lab-job-panel");
    await expect(jobPanel.getByTestId("lab-benchmark-closure-summary")).toBeVisible({ timeout: 60_000 });
    const closureText = (await jobPanel.getByTestId("lab-benchmark-closure-summary").textContent()) ?? "";
    const executedMatch = closureText.match(/(\d+)\s+executed/i);
    expect(executedMatch).not.toBeNull();
    expect(Number(executedMatch![1])).toBeGreaterThan(0);

    await expect(page.getByTestId("lab-benchmark-results-panel")).toBeVisible({ timeout: 60_000 });
    await page.screenshot({ path: path.join(EVIDENCE_DIR, "rag-run-results.png"), fullPage: true });

    const exportJson = await downloadCampaignExportJson(page, campaignId!, "campaign-items.json");
    const exportCsv = await downloadCampaignExportText(page, campaignId!, "items.csv");
    fs.writeFileSync(path.join(EVIDENCE_DIR, "rag-export.json"), JSON.stringify(exportJson, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "rag-export.csv"), exportCsv);
    const items = exportJson.items as Array<Record<string, unknown>> | undefined;
    expect(Array.isArray(items) && items.length > 0).toBe(true);
    expect(exportCsv.length).toBeGreaterThan(20);
  });
});
